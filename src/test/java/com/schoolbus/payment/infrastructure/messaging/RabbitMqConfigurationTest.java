package com.schoolbus.payment.infrastructure.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitMqConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(
                            RabbitMqConfiguration.class
                    )
                    .withPropertyValues(
                            "school-bus.messaging.payment.exchange=schoolbus.payment.events",
                            "school-bus.messaging.payment.refund-routing-key=payment.refund.required",
                            "school-bus.messaging.payment.refund-queue=schoolbus.payment.refund",
                            "school-bus.messaging.payment.dead-letter-exchange=schoolbus.payment.dlx",
                            "school-bus.messaging.payment.dead-letter-routing-key=payment.refund.dead",
                            "school-bus.messaging.payment.dead-letter-queue=schoolbus.payment.refund.dlq",
                            "school-bus.messaging.outbox-relay.enabled=true",
                            "school-bus.messaging.outbox-relay.batch-size=50",
                            "school-bus.messaging.outbox-relay.claim-timeout=30s",
                            "school-bus.messaging.outbox-relay.confirm-timeout=5s",
                            "school-bus.messaging.outbox-relay.initial-retry-delay=5s",
                            "school-bus.messaging.outbox-relay.maximum-retry-delay=5m",
                            "school-bus.messaging.outbox-relay.maximum-attempts=10",
                            "school-bus.messaging.payment-refund-retry.exchange=schoolbus.payment.retry",
                            "school-bus.messaging.payment-refund-retry.routing-key=payment.refund.retry",
                            "school-bus.messaging.payment-refund-retry.queue=schoolbus.payment.refund.retry",
                            "school-bus.messaging.payment-refund-retry.delay=30s",
                            "school-bus.messaging.payment-refund-retry.maximum-retries=3",
                            "school-bus.messaging.payment-refund-retry.confirm-timeout=5s"
                    );

    @Test
    void shouldDeclareDurablePaymentTopology() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            TopicExchange exchange = context.getBean(
                    "paymentEventsExchange",
                    TopicExchange.class
            );
            assertThat(exchange.getName())
                    .isEqualTo("schoolbus.payment.events");
            assertThat(exchange.isDurable()).isTrue();
            assertThat(exchange.isAutoDelete()).isFalse();

            Queue refundQueue = context.getBean(
                    "paymentRefundQueue",
                    Queue.class
            );
            assertThat(refundQueue.getName())
                    .isEqualTo("schoolbus.payment.refund");
            assertThat(refundQueue.isDurable()).isTrue();
            assertThat(refundQueue.isExclusive()).isFalse();
            assertThat(refundQueue.isAutoDelete()).isFalse();
            assertThat(refundQueue.getArguments())
                    .containsEntry(
                            "x-dead-letter-exchange",
                            "schoolbus.payment.dlx"
                    )
                    .containsEntry(
                            "x-dead-letter-routing-key",
                            "payment.refund.dead"
                    );

            DirectExchange deadLetterExchange = context.getBean(
                    "paymentDeadLetterExchange",
                    DirectExchange.class
            );
            assertThat(deadLetterExchange.getName())
                    .isEqualTo("schoolbus.payment.dlx");
            Queue deadLetterQueue = context.getBean(
                    "paymentRefundDeadLetterQueue",
                    Queue.class
            );
            assertThat(deadLetterQueue.getName())
                    .isEqualTo("schoolbus.payment.refund.dlq");

            DirectExchange retryExchange = context.getBean(
                    "paymentRefundRetryExchange",
                    DirectExchange.class
            );
            assertThat(retryExchange.getName())
                    .isEqualTo("schoolbus.payment.retry");
            Queue retryQueue = context.getBean(
                    "paymentRefundRetryQueue",
                    Queue.class
            );
            assertThat(retryQueue.getName())
                    .isEqualTo("schoolbus.payment.refund.retry");
            assertThat(retryQueue.getArguments())
                    .containsEntry("x-message-ttl", 30_000)
                    .containsEntry(
                            "x-dead-letter-exchange",
                            "schoolbus.payment.events"
                    )
                    .containsEntry(
                            "x-dead-letter-routing-key",
                            "payment.refund.required"
                    );
        });
    }

    @Test
    void shouldBindMainAndDeadLetterRoutes() {
        contextRunner.run(context -> {
            Binding refundBinding = context.getBean(
                    "paymentRefundBinding",
                    Binding.class
            );
            assertThat(refundBinding.getExchange())
                    .isEqualTo("schoolbus.payment.events");
            assertThat(refundBinding.getDestination())
                    .isEqualTo("schoolbus.payment.refund");
            assertThat(refundBinding.getRoutingKey())
                    .isEqualTo("payment.refund.required");

            Binding deadLetterBinding = context.getBean(
                    "paymentRefundDeadLetterBinding",
                    Binding.class
            );
            assertThat(deadLetterBinding.getExchange())
                    .isEqualTo("schoolbus.payment.dlx");
            assertThat(deadLetterBinding.getDestination())
                    .isEqualTo("schoolbus.payment.refund.dlq");
            assertThat(deadLetterBinding.getRoutingKey())
                    .isEqualTo("payment.refund.dead");

            Binding retryBinding = context.getBean(
                    "paymentRefundRetryBinding",
                    Binding.class
            );
            assertThat(retryBinding.getExchange())
                    .isEqualTo("schoolbus.payment.retry");
            assertThat(retryBinding.getDestination())
                    .isEqualTo("schoolbus.payment.refund.retry");
            assertThat(retryBinding.getRoutingKey())
                    .isEqualTo("payment.refund.retry");
        });
    }

    @Test
    void shouldFailFastWhenTopologyNameIsBlank() {
        assertThat(new PaymentMessagingProperties(
                "schoolbus.payment.events",
                "payment.refund.required",
                "schoolbus.payment.refund",
                "schoolbus.payment.dlx",
                "payment.refund.dead",
                "schoolbus.payment.refund.dlq"
        ).refundQueue()).isEqualTo("schoolbus.payment.refund");

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> new PaymentMessagingProperties(
                        " ",
                        "payment.refund.required",
                        "schoolbus.payment.refund",
                        "schoolbus.payment.dlx",
                        "payment.refund.dead",
                        "schoolbus.payment.refund.dlq"
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "payment messaging exchange must not be blank"
                );
    }

    @Test
    void shouldEnableReliablePublisherAndManualAcknowledgement() {
        new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(
                                RabbitAutoConfiguration.class
                        )
                )
                .withPropertyValues(
                        "spring.rabbitmq.publisher-confirm-type=correlated",
                        "spring.rabbitmq.publisher-returns=true",
                        "spring.rabbitmq.template.mandatory=true",
                        "spring.rabbitmq.listener.simple.acknowledge-mode=manual",
                        "spring.rabbitmq.listener.simple.prefetch=20"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    CachingConnectionFactory connectionFactory =
                            context.getBean(
                                    CachingConnectionFactory.class
                            );
                    assertThat(connectionFactory.isPublisherConfirms())
                            .isTrue();
                    assertThat(connectionFactory.isPublisherReturns())
                            .isTrue();

                    RabbitTemplate rabbitTemplate = context.getBean(
                            RabbitTemplate.class
                    );
                    assertThat(rabbitTemplate.isMandatoryFor(
                            new Message(new byte[0])
                    )).isTrue();

                    RabbitProperties properties = context.getBean(
                            RabbitProperties.class
                    );
                    assertThat(properties.getListener()
                            .getSimple()
                            .getAcknowledgeMode())
                            .isEqualTo(AcknowledgeMode.MANUAL);
                    assertThat(properties.getListener()
                            .getSimple()
                            .getPrefetch()).isEqualTo(20);
                });
    }
}
