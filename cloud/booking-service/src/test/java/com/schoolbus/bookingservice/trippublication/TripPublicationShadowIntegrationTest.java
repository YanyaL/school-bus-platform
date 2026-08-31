package com.schoolbus.bookingservice.trippublication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolbus.bookingservice.application.trippublication.*;
import com.schoolbus.bookingservice.infrastructure.messaging.trippublication.*;
import com.schoolbus.bookingservice.infrastructure.persistence.trippublication.TripPublicationShadowMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static com.schoolbus.bookingservice.trippublication.PublicationFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalAnswers.delegatesTo;

/** Real SQL/AMQP, isolated containers and actual production configuration; no project business database. */
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TripPublicationShadowIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.0")
            .withDatabaseName("publication_shadow_test").withUsername("shadow").withPassword("shadow")
            .withCommand("--log-bin-trust-function-creators=1");
    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4.1-management");
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private TripPublicationShadowTransaction transaction;
    private TripPublicationShadowMapper realMapper;
    private TripPublicationShadowMapper observedMapper;
    private RabbitTemplate rabbit;
    private RabbitAdmin admin;
    private TripPublicationShadowProperties topology;
    private RabbitListenerEndpointRegistry listeners;
    private MeterRegistry metrics;

    @BeforeAll
    void startIsolatedContext() throws Exception {
        DataSource dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        new ResourceDatabasePopulator(new ClassPathResource("shadow-schema/V8__add_booking_trip_publication_shadow.sql"))
                .execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean(); factory.setDataSource(dataSource);
        var mybatis = new org.apache.ibatis.session.Configuration(); mybatis.setMapUnderscoreToCamelCase(true);
        mybatis.addMapper(TripPublicationShadowMapper.class); factory.setConfiguration(mybatis);
        SqlSessionFactory sessions = factory.getObject();
        realMapper = new SqlSessionTemplate(sessions).getMapper(TripPublicationShadowMapper.class);
        observedMapper = mock(TripPublicationShadowMapper.class, delegatesTo(realMapper));
        CachingConnectionFactory connection = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getAmqpPort());
        connection.setUsername(RABBIT.getAdminUsername()); connection.setPassword(RABBIT.getAdminPassword());
        connection.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        connection.setPublisherReturns(true);
        rabbit = new RabbitTemplate(connection); rabbit.setMandatory(true);

        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("shadow-test", Map.of(
                "school-bus.booking.trip-publication-shadow.enabled", "true",
                "school-bus.booking.trip-publication-shadow.retry-delay-ms", "10")));
        // Plain context needs the Boot properties binder explicitly, but loads the production beans unchanged.
        org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor.register(context);
        context.registerBean(DataSource.class, () -> dataSource);
        context.registerBean(PlatformTransactionManager.class, () -> new DataSourceTransactionManager(dataSource));
        context.registerBean(TripPublicationShadowMapper.class, () -> observedMapper);
        context.registerBean(ObjectMapper.class, () -> JSON);
        context.registerBean(Clock.class, Clock::systemUTC);
        context.registerBean(MeterRegistry.class, SimpleMeterRegistry::new);
        context.registerBean(ConnectionFactory.class, () -> connection);
        context.registerBean(RabbitTemplate.class, () -> rabbit);
        context.registerBean(RabbitAdmin.class, () -> new RabbitAdmin(connection));
        context.registerBean(SimpleRabbitListenerContainerFactoryConfigurer.class, () -> {
            RabbitProperties props = new RabbitProperties(); props.getListener().getSimple().setAutoStartup(false);
            return new SimpleRabbitListenerContainerFactoryConfigurer(props);
        });
        context.register(Infrastructure.class, TripPublicationShadowConfiguration.class);
        context.refresh();
        transaction = context.getBean(TripPublicationShadowTransaction.class);
        admin = context.getBean(RabbitAdmin.class); admin.initialize();
        topology = context.getBean(TripPublicationShadowProperties.class);
        listeners = context.getBean(RabbitListenerEndpointRegistry.class);
        metrics = context.getBean(MeterRegistry.class);
    }

    @AfterAll
    void closeContext() { if (context != null) context.close(); }

    @BeforeEach
    void resetObservationStateOnly() {
        listeners.getListenerContainers().forEach(org.springframework.amqp.rabbit.listener.MessageListenerContainer::stop);
        admin.purgeQueue(topology.queue()); admin.purgeQueue(topology.deadLetterQueue());
        jdbc.update("DELETE FROM booking_trip_publication_inbox");
        jdbc.update("DELETE FROM booking_trip_publication_shadow");
        reset(observedMapper);
    }

    @Test
    void duplicateDeliveryChangesNeitherSnapshotNorTimestamp() {
        assertThat(transaction.observe(event())).isEqualTo(TripPublicationShadowTransaction.Outcome.APPLIED);
        Map<String, Object> before = row();
        assertThat(transaction.observe(event())).isEqualTo(TripPublicationShadowTransaction.Outcome.DUPLICATE);
        assertThat(row()).isEqualTo(before);
        assertThat(inboxCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()", Integer.class))
                .isEqualTo(2); // No live order/seat/inventory table exists in this test DB, so any accidental access fails.
    }

    @Test
    void sameVersionNewIdentityIsAlreadyAppliedAndOlderVersionCannotOverwrite() {
        transaction.observe(version(3));
        Map<String, Object> before = row();
        assertThat(transaction.observe(version(3))).isEqualTo(TripPublicationShadowTransaction.Outcome.ALREADY_APPLIED);
        assertThat(transaction.observe(version(1))).isEqualTo(TripPublicationShadowTransaction.Outcome.STALE);
        assertThat(row()).isEqualTo(before);
        assertThat(inboxCount()).isEqualTo(3);
    }

    @Test
    void newerVersionAdvancesProjection() {
        transaction.observe(version(1)); transaction.observe(version(2));
        assertThat(jdbc.queryForObject("SELECT trip_version FROM booking_trip_publication_shadow", Long.class)).isEqualTo(2);
    }

    @Test
    void sameEventDifferentPayloadAndSameVersionConflictRollBack() {
        transaction.observe(event()); Map<String, Object> before = row();
        TripPublicationMessageDecoder decoder = new TripPublicationMessageDecoder(JSON);
        assertThatThrownBy(() -> transaction.observe(decoder.decode(message(payload().put("price", "6.00"), EVENT_ID))))
                .isInstanceOf(TripPublicationRejectedException.class);
        assertThatThrownBy(() -> transaction.observe(decoder.decode(message(payload().put("price", "6.00"), UUID.randomUUID().toString()))))
                .isInstanceOf(TripPublicationRejectedException.class);
        assertThat(row()).isEqualTo(before); assertThat(inboxCount()).isEqualTo(1);
    }

    @Test
    void finalMarkerFailureRollsBackSnapshotAndInboxTogether() {
        jdbc.execute("CREATE TRIGGER fail_shadow_completion BEFORE UPDATE ON booking_trip_publication_inbox "
                + "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected marker failure'");
        try {
            assertThatThrownBy(() -> transaction.observe(event())).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(inboxCount()).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM booking_trip_publication_shadow", Integer.class)).isZero();
        } finally { jdbc.execute("DROP TRIGGER IF EXISTS fail_shadow_completion"); }
    }

    @Test
    void concurrentDuplicateEventsProduceOneObservation() throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<TripPublicationShadowTransaction.Outcome>> results = new ArrayList<>();
            for (int i = 0; i < 8; i++) results.add(pool.submit(() -> { start.await(); return transaction.observe(event()); }));
            start.countDown();
            int applied = 0;
            for (var result : results) if (result.get(20, TimeUnit.SECONDS) == TripPublicationShadowTransaction.Outcome.APPLIED) applied++;
            assertThat(applied).isEqualTo(1); assertThat(inboxCount()).isEqualTo(1);
        }
    }

    @Test
    void storeRejectsWritesWithoutTransaction() {
        assertThatThrownBy(() -> context.getBean(TripPublicationShadowStore.class).insertInbox(EVENT_ID, 1, "hash", Instant.now()))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }

    @Test
    void realRabbitDuplicateIsObservedAndAcknowledgedAfterCommit() {
        startListeners(); publish(message());
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(inboxCount()).isEqualTo(1));
        awaitDrained(); Map<String, Object> before = row();
        double baseline = metric("DUPLICATE"); publish(message());
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(metric("DUPLICATE")).isEqualTo(baseline + 1));
        awaitDrained();
        assertThat(inboxCount()).isEqualTo(1); assertThat(row()).isEqualTo(before);
    }

    @Test
    void malformedRealMessageIsDeadLetteredWithoutObservation() {
        startListeners(); publish(message(payload().put("schemaVersion", 2), EVENT_ID));
        Message rejected = rabbit.receive(topology.deadLetterQueue(), 10000);
        assertThat(rejected).isNotNull(); assertThat(rejected.getMessageProperties().getMessageId()).isEqualTo(EVENT_ID);
        assertThat(inboxCount()).isZero(); awaitDrained();
    }

    @Test
    void transientDatabaseFailureRetriesAndCommitsRealSql() {
        // Failure injection at the mapper boundary; retry and all subsequent SQL use the real container/transaction.
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(call -> {
            if (attempts.incrementAndGet() == 1) throw new TransientDataAccessResourceException("injected outage");
            return realMapper.insertInbox(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3));
        }).when(observedMapper).insertInbox(anyString(), anyLong(), anyString(), any());
        startListeners(); publish(message());
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(inboxCount()).isEqualTo(1));
        awaitDrained(); assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    void exhaustedTransientFailuresReachRealDlqWithoutPartialState() {
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(call -> { attempts.incrementAndGet(); throw new TransientDataAccessResourceException("injected outage"); })
                .when(observedMapper).insertInbox(anyString(), anyLong(), anyString(), any());
        startListeners(); publish(message());
        Message rejected = rabbit.receive(topology.deadLetterQueue(), 10000);
        assertThat(rejected).isNotNull(); assertThat(rejected.getMessageProperties().getMessageId()).isEqualTo(EVENT_ID);
        assertThat(attempts.get()).isEqualTo(3); assertThat(inboxCount()).isZero(); awaitDrained();
    }

    private int inboxCount() { return jdbc.queryForObject("SELECT COUNT(*) FROM booking_trip_publication_inbox", Integer.class); }
    private Map<String, Object> row() { return jdbc.queryForMap("SELECT * FROM booking_trip_publication_shadow"); }
    private void startListeners() { listeners.getListenerContainers().forEach(org.springframework.amqp.rabbit.listener.MessageListenerContainer::start); }
    private void publish(Message message) { rabbit.send(topology.exchange(), topology.routingKey(), message); }
    private double metric(String outcome) {
        var counter = metrics.find("schoolbus.booking.trip_publication.shadow").tag("outcome", outcome).counter();
        return counter == null ? 0 : counter.count();
    }
    private void awaitDrained() {
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            String auth = Base64.getEncoder().encodeToString((RABBIT.getAdminUsername() + ":" + RABBIT.getAdminPassword()).getBytes(StandardCharsets.UTF_8));
            var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://" + RABBIT.getHost() + ":"
                    + RABBIT.getHttpPort() + "/api/queues/%2F/" + topology.queue()))
                    .timeout(Duration.ofSeconds(3)).header("Authorization", "Basic " + auth).GET().build();
            var response = java.net.http.HttpClient.newHttpClient().send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            var evidence = JSON.readTree(response.body());
            assertThat(evidence.has("messages_ready")).isTrue();
            assertThat(evidence.has("messages_unacknowledged")).isTrue();
            assertThat(evidence.get("messages_ready").asInt()).isZero();
            assertThat(evidence.get("messages_unacknowledged").asInt()).isZero();
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableRabbit
    @EnableTransactionManagement
    static class Infrastructure { }
}
