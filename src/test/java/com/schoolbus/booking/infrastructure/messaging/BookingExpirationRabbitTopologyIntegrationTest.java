package com.schoolbus.booking.infrastructure.messaging;

import com.rabbitmq.client.GetResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers(disabledWithoutDocker = true)
@EnabledIfEnvironmentVariable(
        named = "RUN_MESSAGING_ACCEPTANCE_TESTS",
        matches = "true"
)
class BookingExpirationRabbitTopologyIntegrationTest {

    private static final BookingExpirationMessagingProperties PROPERTIES =
            new BookingExpirationMessagingProperties(
                    "schoolbus.booking.expiration.delay.test",
                    "booking.payment.deadline.delay",
                    "schoolbus.booking.expiration.delay.test",
                    "schoolbus.booking.events.test",
                    "booking.payment.deadline.reached",
                    "schoolbus.booking.expiration.test",
                    "schoolbus.booking.dlx.test",
                    "booking.expiration.dead",
                    "schoolbus.booking.expiration.dlq.test"
            );

    @Container
    static final RabbitMQContainer RABBIT_MQ =
            new RabbitMQContainer(
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

        BookingExpirationRabbitConfiguration configuration =
                new BookingExpirationRabbitConfiguration();
        DirectExchange delayExchange =
                configuration.bookingExpirationDelayExchange(PROPERTIES);
        DirectExchange processingExchange =
                configuration.bookingExpirationProcessingExchange(
                        PROPERTIES
                );
        DirectExchange deadLetterExchange =
                configuration.bookingExpirationDeadLetterExchange(
                        PROPERTIES
                );
        Queue delayQueue = configuration.bookingExpirationDelayQueue(
                PROPERTIES
        );
        Queue processingQueue =
                configuration.bookingExpirationProcessingQueue(
                        PROPERTIES
                );
        Queue deadLetterQueue =
                configuration.bookingExpirationDeadLetterQueue(
                        PROPERTIES
                );
        Binding delayBinding = configuration.bookingExpirationDelayBinding(
                delayQueue,
                delayExchange,
                PROPERTIES
        );
        Binding processingBinding =
                configuration.bookingExpirationProcessingBinding(
                        processingQueue,
                        processingExchange,
                        PROPERTIES
                );
        Binding deadLetterBinding =
                configuration.bookingExpirationDeadLetterBinding(
                        deadLetterQueue,
                        deadLetterExchange,
                        PROPERTIES
                );

        RabbitAdmin rabbitAdmin = new RabbitAdmin(connectionFactory);
        rabbitAdmin.declareExchange(delayExchange);
        rabbitAdmin.declareExchange(processingExchange);
        rabbitAdmin.declareExchange(deadLetterExchange);
        rabbitAdmin.declareQueue(delayQueue);
        rabbitAdmin.declareQueue(processingQueue);
        rabbitAdmin.declareQueue(deadLetterQueue);
        rabbitAdmin.declareBinding(delayBinding);
        rabbitAdmin.declareBinding(processingBinding);
        rabbitAdmin.declareBinding(deadLetterBinding);

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
    void shouldRouteTtlExpiredDelayMessageToProcessingQueue() {
        String payload = "{\"bookingId\":5001,\"expiresAt\":\"2026-08-11T00:15:00Z\"}";
        MessageProperties properties = new MessageProperties();
        properties.setExpiration("500");
        Message message = new Message(
                payload.getBytes(StandardCharsets.UTF_8),
                properties
        );

        rabbitTemplate.send(
                PROPERTIES.delayExchange(),
                PROPERTIES.delayRoutingKey(),
                message
        );

        await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(
                        rabbitTemplate.receiveAndConvert(
                                PROPERTIES.processingQueue()
                        )
                ).isEqualTo(payload));
    }

    @Test
    void shouldDeadLetterRejectedProcessingMessage() throws Exception {
        String payload = "malformed-expiration-event";
        rabbitTemplate.convertAndSend(
                PROPERTIES.processingExchange(),
                PROPERTIES.processingRoutingKey(),
                payload
        );

        try (var connection = connectionFactory.createConnection();
             var channel = connection.createChannel(false)) {
            GetResponse response = channel.basicGet(
                    PROPERTIES.processingQueue(),
                    false
            );
            assertThat(response).isNotNull();
            channel.basicReject(
                    response.getEnvelope().getDeliveryTag(),
                    false
            );
        }

        await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(
                        rabbitTemplate.receiveAndConvert(
                                PROPERTIES.deadLetterQueue()
                        )
                ).isEqualTo(payload));
    }
}
