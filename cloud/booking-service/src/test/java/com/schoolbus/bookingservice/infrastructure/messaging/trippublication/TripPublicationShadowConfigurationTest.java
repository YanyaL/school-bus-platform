package com.schoolbus.bookingservice.infrastructure.messaging.trippublication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolbus.bookingservice.application.trippublication.*;
import com.schoolbus.bookingservice.infrastructure.persistence.trippublication.TripPublicationShadowMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.dao.*;
import org.springframework.retry.support.RetryTemplate;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class TripPublicationShadowConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TripPublicationShadowConfiguration.class);

    @Test
    void defaultDisabledHasNoConsumerTransactionOrTopologyDependencies() {
        runner.run(c -> {
            assertThat(c).doesNotHaveBean(TripPublicationShadowListener.class);
            assertThat(c).doesNotHaveBean(TripPublicationShadowTransaction.class);
            assertThat(c).doesNotHaveBean(Declarables.class);
        });
    }
    @Test
    void explicitEnabledBindsCustomIndependentQueueAndAutoAckAfterCommit() {
        enabled().withPropertyValues("school-bus.booking.trip-publication-shadow.queue=verify.booking.shadow")
                .run(c -> {
                    assertThat(c).hasSingleBean(TripPublicationShadowListener.class);
                    assertThat(c.getBean(TripPublicationShadowProperties.class).queue()).isEqualTo("verify.booking.shadow");
                    var queues = c.getBean(Declarables.class).getDeclarablesByType(Queue.class);
                    assertThat(queues).hasSize(2);
                    Queue queue = queues.stream().filter(q -> q.getName().equals("verify.booking.shadow")).findFirst().orElseThrow();
                    assertThat(queue.isDurable()).isTrue();
                    assertThat(queue.getArguments()).containsEntry("x-dead-letter-routing-key", "rejected")
                            .containsEntry("x-overflow", "reject-publish");
                    var factory = c.getBean("tripPublicationShadowContainerFactory",
                            org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory.class);
                    assertThat(factory.createListenerContainer().getAcknowledgeMode()).isEqualTo(AcknowledgeMode.AUTO);
                });
    }
    @Test
    void rejectsQueueCollisionOrUnboundedRetryConfiguration() {
        enabled().withPropertyValues("school-bus.booking.trip-publication-shadow.queue=schoolbus.transport.trip-published.shadow")
                .run(c -> assertThat(c).hasFailed());
        enabled().withPropertyValues("school-bus.booking.trip-publication-shadow.maximum-attempts=100")
                .run(c -> assertThat(c).hasFailed());
    }
    @Test
    void onlyKnownTransientDatabaseFailuresAreRetriedWithBoundedAttempts() {
        var properties = new TripPublicationShadowProperties("events", "key", "observe", "dead", "dlq", 3, 1);
        for (RuntimeException failure : new RuntimeException[]{new CannotAcquireLockException("busy"),
                new DataAccessResourceFailureException("offline"), new TripPublicationRejectedException("bad input"),
                new InvalidDataAccessResourceUsageException("bad SQL")}) {
            AtomicInteger calls = new AtomicInteger();
            RetryTemplate retry = new RetryTemplate(); retry.setRetryPolicy(TripPublicationShadowConfiguration.retryPolicy(properties));
            assertThatThrownBy(() -> retry.execute(ctx -> { calls.incrementAndGet(); throw failure; })).isSameAs(failure);
            assertThat(calls.get()).isEqualTo(failure instanceof TransientDataAccessException || failure instanceof DataAccessResourceFailureException ? 3 : 1);
        }
    }

    @Test
    void realSpringProxiesCommitTogetherAndRollbackOnMarkerFailureWithoutDocker() {
        enabled().withUserConfiguration(TransactionInfrastructure.class)
                .withBean(RecordingTransactionManager.class, RecordingTransactionManager::new)
                .run(c -> {
                    var mapper = c.getBean(TripPublicationShadowMapper.class);
                    org.mockito.Mockito.when(mapper.insertInbox(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong(),
                            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(1);
                    org.mockito.Mockito.when(mapper.insertSnapshot(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(),
                            org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(1);
                    org.mockito.Mockito.when(mapper.completeInbox(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).thenReturn(1);
                    var service = c.getBean(TripPublicationShadowTransaction.class);
                    var tx = c.getBean(RecordingTransactionManager.class);
                    assertThat(org.springframework.aop.support.AopUtils.isAopProxy(service)).isTrue();
                    assertThat(service.observe(com.schoolbus.bookingservice.trippublication.PublicationFixtures.event()))
                            .isEqualTo(TripPublicationShadowTransaction.Outcome.APPLIED);
                    assertThat(tx.commits).isEqualTo(1);
                    assertThatThrownBy(() -> c.getBean(TripPublicationShadowStore.class).insertInbox("id", 1, "hash", java.time.Instant.now()))
                            .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
                    org.mockito.Mockito.when(mapper.completeInbox(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                            .thenThrow(new DataAccessResourceFailureException("failed completion"));
                    assertThatThrownBy(() -> service.observe(com.schoolbus.bookingservice.trippublication.PublicationFixtures.event()))
                            .isInstanceOf(DataAccessResourceFailureException.class);
                    assertThat(tx.commits).isEqualTo(1);
                    assertThat(tx.rollbacks).isEqualTo(1);
                });
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @org.springframework.transaction.annotation.EnableTransactionManagement
    static class TransactionInfrastructure { }

    static class RecordingTransactionManager extends org.springframework.transaction.support.AbstractPlatformTransactionManager {
        private final ThreadLocal<Boolean> active = ThreadLocal.withInitial(() -> false);
        int commits;
        int rollbacks;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected boolean isExistingTransaction(Object transaction) { return active.get(); }
        @Override protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) { active.set(true); }
        @Override protected void doCommit(org.springframework.transaction.support.DefaultTransactionStatus status) { commits++; }
        @Override protected void doRollback(org.springframework.transaction.support.DefaultTransactionStatus status) { rollbacks++; }
        @Override protected void doSetRollbackOnly(org.springframework.transaction.support.DefaultTransactionStatus status) { }
        @Override protected void doCleanupAfterCompletion(Object transaction) { active.remove(); }
    }
    private ApplicationContextRunner enabled() {
        return runner.withPropertyValues("school-bus.booking.trip-publication-shadow.enabled=true")
                .withBean(TripPublicationShadowMapper.class, () -> mock(TripPublicationShadowMapper.class))
                .withBean(ObjectMapper.class, ObjectMapper::new).withBean(Clock.class, Clock::systemUTC)
                .withBean(io.micrometer.core.instrument.MeterRegistry.class, io.micrometer.core.instrument.simple.SimpleMeterRegistry::new)
                .withBean(ConnectionFactory.class, () -> mock(ConnectionFactory.class))
                .withBean(SimpleRabbitListenerContainerFactoryConfigurer.class, () -> mock(SimpleRabbitListenerContainerFactoryConfigurer.class));
    }
}
