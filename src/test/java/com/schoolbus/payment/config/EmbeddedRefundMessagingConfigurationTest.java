package com.schoolbus.payment.config;

import com.schoolbus.payment.application.refund.ConsumedEventRepository;
import com.schoolbus.payment.application.refund.PaymentRefundApplicationService;
import com.schoolbus.payment.application.refund.PaymentRefundTransaction;
import com.schoolbus.payment.application.refund.RefundGateway;
import com.schoolbus.payment.application.refund.RefundedBookingPort;
import com.schoolbus.payment.domain.PaymentRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolbus.payment.infrastructure.messaging.OutboxRelayProperties;
import com.schoolbus.payment.infrastructure.messaging.PaymentMessagingProperties;
import com.schoolbus.payment.infrastructure.messaging.PaymentRefundListener;
import com.schoolbus.payment.infrastructure.messaging.RabbitOutboxEventPublisher;
import com.schoolbus.payment.infrastructure.messaging.RabbitMqConfiguration;
import com.schoolbus.payment.infrastructure.messaging.RabbitRefundRetryPublisher;
import com.schoolbus.payment.infrastructure.messaging.RefundRetryAttemptResolver;
import com.schoolbus.payment.infrastructure.messaging.RefundRetryProperties;
import com.schoolbus.payment.infrastructure.outbox.MyBatisOutboxRelayRepository;
import com.schoolbus.payment.infrastructure.outbox.OutboxMapper;
import com.schoolbus.payment.infrastructure.outbox.PaymentRefundOutboxRelay;
import com.schoolbus.payment.infrastructure.outbox.PaymentRefundOutboxRelayScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class EmbeddedRefundMessagingConfigurationTest {

    private static final Class<?>[] EMBEDDED_REFUND_BEANS = {
            PaymentRefundOutboxRelay.class,
            PaymentRefundOutboxRelayScheduler.class,
            PaymentRefundListener.class,
            RabbitOutboxEventPublisher.class,
            RabbitRefundRetryPublisher.class,
            PaymentRefundApplicationService.class,
            PaymentRefundTransaction.class,
    };

    private final ApplicationContextRunner contextRunner =
            embeddedRefundContextRunner();

    @Test
    void keepsEmbeddedRefundBeansByDefault() {
        contextRunner.run(context -> {
            for (Class<?> beanType : EMBEDDED_REFUND_BEANS) {
                assertThat(context).hasSingleBean(beanType);
            }
        });
    }

    @Test
    void removesEmbeddedRefundBeansWhenPaymentOwnsMessaging() {
        embeddedRefundContextRunner()
                .withPropertyValues(
                        "school-bus.payment.refund-messaging.embedded=false"
                )
                .run(context -> {
                    for (Class<?> beanType : EMBEDDED_REFUND_BEANS) {
                        assertThat(context).doesNotHaveBean(beanType);
                    }
                });
    }

    @Test
    void keepsSharedMessagingPropertiesWhenPaymentOwnsRefundMessaging() {
        new ApplicationContextRunner()
                .withPropertyValues(
                        "school-bus.payment.refund-messaging.embedded=false",
                        "school-bus.messaging.outbox-relay.enabled=true",
                        "school-bus.messaging.outbox-relay.batch-size=50",
                        "school-bus.messaging.outbox-relay.claim-timeout=PT30S",
                        "school-bus.messaging.outbox-relay.confirm-timeout=PT5S",
                        "school-bus.messaging.outbox-relay.initial-retry-delay=PT5S",
                        "school-bus.messaging.outbox-relay.maximum-retry-delay=PT5M",
                        "school-bus.messaging.outbox-relay.maximum-attempts=10",
                        "school-bus.messaging.payment.exchange=schoolbus.payment.events",
                        "school-bus.messaging.payment.refund-routing-key=payment.refund.required",
                        "school-bus.messaging.payment.refund-queue=schoolbus.payment.refund",
                        "school-bus.messaging.payment.dead-letter-exchange=schoolbus.payment.dlx",
                        "school-bus.messaging.payment.dead-letter-routing-key=payment.refund.dead",
                        "school-bus.messaging.payment.dead-letter-queue=schoolbus.payment.refund.dlq",
                        "school-bus.messaging.payment.succeeded-routing-key=payment.succeeded",
                        "school-bus.messaging.payment.succeeded-queue=schoolbus.booking.payment-succeeded",
                        "school-bus.messaging.payment.succeeded-dead-letter-routing-key=payment.succeeded.dead",
                        "school-bus.messaging.payment.succeeded-dead-letter-queue=schoolbus.booking.payment-succeeded.dlq",
                        "school-bus.messaging.payment-refund-retry.exchange=schoolbus.payment.retry",
                        "school-bus.messaging.payment-refund-retry.routing-key=payment.refund.retry",
                        "school-bus.messaging.payment-refund-retry.queue=schoolbus.payment.refund.retry",
                        "school-bus.messaging.payment-refund-retry.delay=PT30S",
                        "school-bus.messaging.payment-refund-retry.maximum-retries=3",
                        "school-bus.messaging.payment-refund-retry.confirm-timeout=PT5S",
                        "school-bus.messaging.payment-succeeded-retry.exchange=schoolbus.booking.retry",
                        "school-bus.messaging.payment-succeeded-retry.routing-key=payment.succeeded.retry",
                        "school-bus.messaging.payment-succeeded-retry.queue=schoolbus.booking.payment-succeeded.retry",
                        "school-bus.messaging.payment-succeeded-retry.delay=PT30S",
                        "school-bus.messaging.payment-succeeded-retry.maximum-retries=3",
                        "school-bus.messaging.payment-succeeded-retry.confirm-timeout=PT5S"
                )
                .withUserConfiguration(RabbitMqConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(OutboxRelayProperties.class);
                    assertThat(context).hasSingleBean(PaymentMessagingProperties.class);
                    assertThat(context).hasSingleBean(RefundRetryProperties.class);
                });
    }

    @Test
    void keepsSharedOutboxRepositoryWhenPaymentOwnsRefundMessaging() {
        new ApplicationContextRunner()
                .withPropertyValues(
                        "school-bus.payment.refund-messaging.embedded=false"
                )
                .withBean(OutboxMapper.class, () -> mock(OutboxMapper.class))
                .withUserConfiguration(SharedOutboxRepositoryImport.class)
                .run(context -> assertThat(context)
                        .hasSingleBean(MyBatisOutboxRelayRepository.class));
    }

    private static ApplicationContextRunner embeddedRefundContextRunner() {
        return new ApplicationContextRunner()
                .withPropertyValues(
                        "spring.profiles.active=local",
                        "school-bus.messaging.outbox-relay.enabled=true"
                )
                .withBean(
                        MyBatisOutboxRelayRepository.class,
                        () -> mock(MyBatisOutboxRelayRepository.class)
                )
                .withBean(
                        OutboxRelayProperties.class,
                        () -> new OutboxRelayProperties(
                                true,
                                50,
                                Duration.ofSeconds(30),
                                Duration.ofSeconds(5),
                                Duration.ofSeconds(5),
                                Duration.ofMinutes(5),
                                10
                        )
                )
                .withBean(Clock.class, Clock::systemUTC)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(RabbitTemplate.class, () -> mock(RabbitTemplate.class))
                .withBean(
                        PaymentMessagingProperties.class,
                        () -> new PaymentMessagingProperties(
                                "schoolbus.payment.events",
                                "payment.refund.required",
                                "schoolbus.payment.refund",
                                "schoolbus.payment.dlx",
                                "payment.refund.dead",
                                "schoolbus.payment.refund.dlq"
                        )
                )
                .withBean(
                        RefundRetryProperties.class,
                        () -> new RefundRetryProperties(
                                "schoolbus.payment.retry",
                                "payment.refund.retry",
                                "schoolbus.payment.refund.retry",
                                Duration.ofSeconds(30),
                                3,
                                Duration.ofSeconds(5)
                        )
                )
                .withBean(RefundRetryAttemptResolver.class, RefundRetryAttemptResolver::new)
                .withBean(PaymentRecordRepository.class, () -> mock(PaymentRecordRepository.class))
                .withBean(
                        ConsumedEventRepository.class,
                        () -> mock(ConsumedEventRepository.class)
                )
                .withBean(RefundedBookingPort.class, () -> mock(RefundedBookingPort.class))
                .withBean(RefundGateway.class, () -> mock(RefundGateway.class))
                .withUserConfiguration(EmbeddedRefundBeansImport.class);
    }

    @Configuration(proxyBeanMethods = false)
    @Import({
            PaymentRefundOutboxRelay.class,
            PaymentRefundOutboxRelayScheduler.class,
            PaymentRefundListener.class,
            RabbitOutboxEventPublisher.class,
            RabbitRefundRetryPublisher.class,
            PaymentRefundApplicationService.class,
            PaymentRefundTransaction.class,
    })
    static class EmbeddedRefundBeansImport {
    }

    @Configuration(proxyBeanMethods = false)
    @Import(MyBatisOutboxRelayRepository.class)
    static class SharedOutboxRepositoryImport {
    }
}
