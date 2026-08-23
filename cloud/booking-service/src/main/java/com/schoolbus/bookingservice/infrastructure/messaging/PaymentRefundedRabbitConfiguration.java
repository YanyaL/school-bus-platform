package com.schoolbus.bookingservice.infrastructure.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("!test")
@EnableConfigurationProperties(PaymentRefundedRetryProperties.class)
public class PaymentRefundedRabbitConfiguration {

    @Bean
    DirectExchange paymentRefundedRetryExchange(
            PaymentRefundedRetryProperties properties
    ) {
        return new DirectExchange(properties.exchange(), true, false);
    }

    @Bean
    Queue paymentRefundedQueue(PaymentMessagingProperties properties) {
        return QueueBuilder
                .durable(properties.refundedQueue())
                .deadLetterExchange(properties.deadLetterExchange())
                .deadLetterRoutingKey(
                        properties.refundedDeadLetterRoutingKey()
                )
                .build();
    }

    @Bean
    Queue paymentRefundedDeadLetterQueue(
            PaymentMessagingProperties properties
    ) {
        return QueueBuilder
                .durable(properties.refundedDeadLetterQueue())
                .build();
    }

    @Bean
    Queue paymentRefundedRetryQueue(
            PaymentRefundedRetryProperties retryProperties,
            PaymentMessagingProperties paymentProperties
    ) {
        return QueueBuilder
                .durable(retryProperties.queue())
                .ttl(Math.toIntExact(retryProperties.delay().toMillis()))
                .deadLetterExchange(paymentProperties.exchange())
                .deadLetterRoutingKey(
                        paymentProperties.refundedRoutingKey()
                )
                .build();
    }

    @Bean
    Binding paymentRefundedBinding(
            @Qualifier("paymentRefundedQueue") Queue queue,
            @Qualifier("paymentEventsExchange") TopicExchange exchange,
            PaymentMessagingProperties properties
    ) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(properties.refundedRoutingKey());
    }

    @Bean
    Binding paymentRefundedDeadLetterBinding(
            @Qualifier("paymentRefundedDeadLetterQueue") Queue queue,
            @Qualifier("paymentDeadLetterExchange") DirectExchange exchange,
            PaymentMessagingProperties properties
    ) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(properties.refundedDeadLetterRoutingKey());
    }

    @Bean
    Binding paymentRefundedRetryBinding(
            @Qualifier("paymentRefundedRetryQueue") Queue queue,
            @Qualifier("paymentRefundedRetryExchange") DirectExchange exchange,
            PaymentRefundedRetryProperties properties
    ) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(properties.routingKey());
    }
}
