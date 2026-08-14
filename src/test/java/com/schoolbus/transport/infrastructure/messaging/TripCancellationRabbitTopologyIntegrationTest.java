package com.schoolbus.transport.infrastructure.messaging;

import com.rabbitmq.client.GetResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers(disabledWithoutDocker = true)
@EnabledIfEnvironmentVariable(
        named = "RUN_MESSAGING_ACCEPTANCE_TESTS",
        matches = "true"
)
class TripCancellationRabbitTopologyIntegrationTest {

    private static final TripCancellationMessagingProperties PROPERTIES =
            new TripCancellationMessagingProperties(
                    "schoolbus.transport.events.cancellation-test",
                    "trip.cancellation.requested",
                    "schoolbus.booking.trip-cancellation.test",
                    "trip.cancellation.settled",
                    "schoolbus.transport.trip-cancellation-settled.test",
                    "schoolbus.transport.cancellation-dlx.test",
                    "trip.cancellation.dead",
                    "schoolbus.transport.trip-cancellation.dlq.test"
            );

    private static final TripCancellationRetryProperties RETRY_PROPERTIES =
            new TripCancellationRetryProperties(
                    "schoolbus.transport.cancellation.retry-test",
                    "trip.cancellation.requested.retry",
                    "schoolbus.booking.trip-cancellation.retry-test",
                    "trip.cancellation.settled.retry",
                    "schoolbus.transport.trip-cancellation-settled.retry-test",
                    Duration.ofMillis(250),
                    3,
                    Duration.ofSeconds(5)
            );

    @Container
    static final RabbitMQContainer RABBIT_MQ = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:4.1-management")
    );

    private CachingConnectionFactory connectionFactory;
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void declareTopology() {
        connectionFactory = new CachingConnectionFactory(
                RABBIT_MQ.getHost(),
                RABBIT_MQ.getAmqpPort()
        );
        connectionFactory.setUsername(RABBIT_MQ.getAdminUsername());
        connectionFactory.setPassword(RABBIT_MQ.getAdminPassword());

        TripCancellationRabbitConfiguration configuration =
                new TripCancellationRabbitConfiguration();
        TopicExchange exchange = configuration
                .tripCancellationExchange(PROPERTIES);
        DirectExchange deadLetterExchange = configuration
                .tripCancellationDeadLetterExchange(PROPERTIES);
        DirectExchange retryExchange = configuration
                .tripCancellationRetryExchange(RETRY_PROPERTIES);
        Queue requestedQueue = configuration
                .tripCancellationRequestedQueue(PROPERTIES);
        Queue settledQueue = configuration
                .tripCancellationSettledQueue(PROPERTIES);
        Queue deadLetterQueue = configuration
                .tripCancellationDeadLetterQueue(PROPERTIES);
        Queue requestedRetryQueue = configuration
                .tripCancellationRequestedRetryQueue(
                        RETRY_PROPERTIES,
                        PROPERTIES
                );
        Queue settledRetryQueue = configuration
                .tripCancellationSettledRetryQueue(
                        RETRY_PROPERTIES,
                        PROPERTIES
                );
        Binding requestedBinding = configuration
                .tripCancellationRequestedBinding(
                        requestedQueue,
                        exchange,
                        PROPERTIES
                );
        Binding settledBinding = configuration
                .tripCancellationSettledBinding(
                        settledQueue,
                        exchange,
                        PROPERTIES
                );
        Binding deadLetterBinding = configuration
                .tripCancellationDeadLetterBinding(
                        deadLetterQueue,
                        deadLetterExchange,
                        PROPERTIES
                );
        Binding requestedRetryBinding = configuration
                .tripCancellationRequestedRetryBinding(
                        requestedRetryQueue,
                        retryExchange,
                        RETRY_PROPERTIES
                );
        Binding settledRetryBinding = configuration
                .tripCancellationSettledRetryBinding(
                        settledRetryQueue,
                        retryExchange,
                        RETRY_PROPERTIES
                );

        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        admin.declareExchange(exchange);
        admin.declareExchange(deadLetterExchange);
        admin.declareExchange(retryExchange);
        admin.declareQueue(requestedQueue);
        admin.declareQueue(settledQueue);
        admin.declareQueue(deadLetterQueue);
        admin.declareQueue(requestedRetryQueue);
        admin.declareQueue(settledRetryQueue);
        admin.declareBinding(requestedBinding);
        admin.declareBinding(settledBinding);
        admin.declareBinding(deadLetterBinding);
        admin.declareBinding(requestedRetryBinding);
        admin.declareBinding(settledRetryBinding);

        rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setReceiveTimeout(5_000L);
    }

    @AfterEach
    void closeConnectionFactory() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void shouldRouteRequestedAndSettledEventsSeparately() {
        rabbitTemplate.convertAndSend(
                PROPERTIES.exchange(),
                PROPERTIES.requestedRoutingKey(),
                "requested"
        );
        rabbitTemplate.convertAndSend(
                PROPERTIES.exchange(),
                PROPERTIES.settledRoutingKey(),
                "settled"
        );

        assertThat(rabbitTemplate.receiveAndConvert(
                PROPERTIES.requestedQueue()
        )).isEqualTo("requested");
        assertThat(rabbitTemplate.receiveAndConvert(
                PROPERTIES.settledQueue()
        )).isEqualTo("settled");
    }

    @Test
    void shouldDeadLetterRejectedCancellationMessage() throws Exception {
        rabbitTemplate.convertAndSend(
                PROPERTIES.exchange(),
                PROPERTIES.requestedRoutingKey(),
                "malformed"
        );
        try (var connection = connectionFactory.createConnection();
             var channel = connection.createChannel(false)) {
            GetResponse response = channel.basicGet(
                    PROPERTIES.requestedQueue(),
                    false
            );
            assertThat(response).isNotNull();
            channel.basicReject(
                    response.getEnvelope().getDeliveryTag(),
                    false
            );
        }

        await().atMost(Duration.ofSeconds(5)).untilAsserted(
                () -> assertThat(rabbitTemplate.receiveAndConvert(
                        PROPERTIES.deadLetterQueue()
                )).isEqualTo("malformed")
        );
    }

    @Test
    void shouldReturnRequestedAndSettledRetriesAfterDelay() {
        rabbitTemplate.convertAndSend(
                RETRY_PROPERTIES.exchange(),
                RETRY_PROPERTIES.requestedRoutingKey(),
                "requested-retry"
        );
        rabbitTemplate.convertAndSend(
                RETRY_PROPERTIES.exchange(),
                RETRY_PROPERTIES.settledRoutingKey(),
                "settled-retry"
        );

        await().atMost(Duration.ofSeconds(5)).untilAsserted(
                () -> assertThat(rabbitTemplate.receiveAndConvert(
                        PROPERTIES.requestedQueue()
                )).isEqualTo("requested-retry")
        );
        await().atMost(Duration.ofSeconds(5)).untilAsserted(
                () -> assertThat(rabbitTemplate.receiveAndConvert(
                        PROPERTIES.settledQueue()
                )).isEqualTo("settled-retry")
        );
    }
}
