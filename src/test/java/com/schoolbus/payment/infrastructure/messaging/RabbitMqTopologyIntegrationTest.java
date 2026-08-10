package com.schoolbus.payment.infrastructure.messaging;

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
        named = "RUN_RABBITMQ_INTEGRATION_TESTS",
        matches = "true"
)
class RabbitMqTopologyIntegrationTest {

    private static final PaymentMessagingProperties PROPERTIES =
            new PaymentMessagingProperties(
                    "schoolbus.payment.events.test",
                    "payment.refund.required",
                    "schoolbus.payment.refund.test",
                    "schoolbus.payment.dlx.test",
                    "payment.refund.dead",
                    "schoolbus.payment.refund.dlq.test"
            );
    private static final RefundRetryProperties RETRY_PROPERTIES =
            new RefundRetryProperties(
                    "schoolbus.payment.retry.test",
                    "payment.refund.retry",
                    "schoolbus.payment.refund.retry.test",
                    Duration.ofMillis(200),
                    3,
                    Duration.ofSeconds(5)
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

        RabbitMqConfiguration configuration =
                new RabbitMqConfiguration();
        TopicExchange eventsExchange =
                configuration.paymentEventsExchange(PROPERTIES);
        DirectExchange deadLetterExchange =
                configuration.paymentDeadLetterExchange(PROPERTIES);
        DirectExchange retryExchange =
                configuration.paymentRefundRetryExchange(
                        RETRY_PROPERTIES
                );
        Queue refundQueue = configuration.paymentRefundQueue(
                PROPERTIES
        );
        Queue deadLetterQueue =
                configuration.paymentRefundDeadLetterQueue(
                        PROPERTIES
                );
        Queue retryQueue = configuration.paymentRefundRetryQueue(
                RETRY_PROPERTIES,
                PROPERTIES
        );
        Binding refundBinding = configuration.paymentRefundBinding(
                refundQueue,
                eventsExchange,
                PROPERTIES
        );
        Binding deadLetterBinding =
                configuration.paymentRefundDeadLetterBinding(
                        deadLetterQueue,
                        deadLetterExchange,
                        PROPERTIES
                );
        Binding retryBinding = configuration.paymentRefundRetryBinding(
                retryQueue,
                retryExchange,
                RETRY_PROPERTIES
        );

        RabbitAdmin rabbitAdmin = new RabbitAdmin(connectionFactory);
        rabbitAdmin.declareExchange(eventsExchange);
        rabbitAdmin.declareExchange(deadLetterExchange);
        rabbitAdmin.declareExchange(retryExchange);
        rabbitAdmin.declareQueue(refundQueue);
        rabbitAdmin.declareQueue(deadLetterQueue);
        rabbitAdmin.declareQueue(retryQueue);
        rabbitAdmin.declareBinding(refundBinding);
        rabbitAdmin.declareBinding(deadLetterBinding);
        rabbitAdmin.declareBinding(retryBinding);

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
    void shouldRouteRefundEventAndDeadLetterRejectedMessage()
            throws Exception {
        String routableMessage = "refund-required-1";
        rabbitTemplate.convertAndSend(
                PROPERTIES.exchange(),
                PROPERTIES.refundRoutingKey(),
                routableMessage
        );

        assertThat(rabbitTemplate.receiveAndConvert(
                PROPERTIES.refundQueue()
        )).isEqualTo(routableMessage);

        String rejectedMessage = "refund-required-2";
        rabbitTemplate.convertAndSend(
                PROPERTIES.exchange(),
                PROPERTIES.refundRoutingKey(),
                rejectedMessage
        );
        try (var connection = connectionFactory.createConnection();
             var channel = connection.createChannel(false)) {
            GetResponse response = channel.basicGet(
                    PROPERTIES.refundQueue(),
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
                ).isEqualTo(rejectedMessage));
    }

    @Test
    void shouldReturnExpiredRetryMessageToRefundQueue() {
        String retryMessage = "refund-retry-1";
        rabbitTemplate.convertAndSend(
                RETRY_PROPERTIES.exchange(),
                RETRY_PROPERTIES.routingKey(),
                retryMessage
        );

        await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(
                        rabbitTemplate.receiveAndConvert(
                                PROPERTIES.refundQueue()
                        )
                ).isEqualTo(retryMessage));
    }
}
