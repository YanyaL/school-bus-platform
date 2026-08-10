package com.schoolbus.payment.infrastructure.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        PaymentMessagingProperties.class,
        OutboxRelayProperties.class
})
public class RabbitMqConfiguration {

    @Bean
    TopicExchange paymentEventsExchange(
            PaymentMessagingProperties properties
    ) {
        return new TopicExchange(
                properties.exchange(),
                true,
                false
        );
    }

    @Bean
    DirectExchange paymentDeadLetterExchange(
            PaymentMessagingProperties properties
    ) {
        return new DirectExchange(
                properties.deadLetterExchange(),
                true,
                false
        );
    }

    @Bean
    Queue paymentRefundQueue(
            PaymentMessagingProperties properties
    ) {
        return QueueBuilder
                .durable(properties.refundQueue())
                .deadLetterExchange(
                        properties.deadLetterExchange()
                )
                .deadLetterRoutingKey(
                        properties.deadLetterRoutingKey()
                )
                .build();
    }

    @Bean
    Queue paymentRefundDeadLetterQueue(
            PaymentMessagingProperties properties
    ) {
        return QueueBuilder
                .durable(properties.deadLetterQueue())
                .build();
    }

    @Bean
    Binding paymentRefundBinding(
            @Qualifier("paymentRefundQueue")
            Queue paymentRefundQueue,
            @Qualifier("paymentEventsExchange")
            TopicExchange paymentEventsExchange,
            PaymentMessagingProperties properties
    ) {
        return BindingBuilder
                .bind(paymentRefundQueue)
                .to(paymentEventsExchange)
                .with(properties.refundRoutingKey());
    }

    @Bean
    Binding paymentRefundDeadLetterBinding(
            @Qualifier("paymentRefundDeadLetterQueue")
            Queue paymentRefundDeadLetterQueue,
            @Qualifier("paymentDeadLetterExchange")
            DirectExchange paymentDeadLetterExchange,
            PaymentMessagingProperties properties
    ) {
        return BindingBuilder
                .bind(paymentRefundDeadLetterQueue)
                .to(paymentDeadLetterExchange)
                .with(properties.deadLetterRoutingKey());
    }
}
