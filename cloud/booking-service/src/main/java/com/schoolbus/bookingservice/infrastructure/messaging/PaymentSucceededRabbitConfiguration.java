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
@EnableConfigurationProperties({
        PaymentMessagingProperties.class,
        PaymentSucceededRetryProperties.class
})
public class PaymentSucceededRabbitConfiguration {

    @Bean
    TopicExchange paymentEventsExchange(
            PaymentMessagingProperties properties
    ) {
        return new TopicExchange(properties.exchange(), true, false);
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
    DirectExchange paymentSucceededRetryExchange(
            PaymentSucceededRetryProperties properties
    ) {
        return new DirectExchange(properties.exchange(), true, false);
    }

    @Bean
    Queue paymentSucceededQueue(PaymentMessagingProperties properties) {
        return QueueBuilder
                .durable(properties.succeededQueue())
                .deadLetterExchange(properties.deadLetterExchange())
                .deadLetterRoutingKey(
                        properties.succeededDeadLetterRoutingKey()
                )
                .build();
    }

    @Bean
    Queue paymentSucceededDeadLetterQueue(
            PaymentMessagingProperties properties
    ) {
        return QueueBuilder
                .durable(properties.succeededDeadLetterQueue())
                .build();
    }

    @Bean
    Queue paymentSucceededRetryQueue(
            PaymentSucceededRetryProperties retryProperties,
            PaymentMessagingProperties paymentProperties
    ) {
        return QueueBuilder
                .durable(retryProperties.queue())
                .ttl(Math.toIntExact(retryProperties.delay().toMillis()))
                .deadLetterExchange(paymentProperties.exchange())
                .deadLetterRoutingKey(
                        paymentProperties.succeededRoutingKey()
                )
                .build();
    }

    @Bean
    Binding paymentSucceededBinding(
            @Qualifier("paymentSucceededQueue") Queue queue,
            @Qualifier("paymentEventsExchange") TopicExchange exchange,
            PaymentMessagingProperties properties
    ) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(properties.succeededRoutingKey());
    }

    @Bean
    Binding paymentSucceededDeadLetterBinding(
            @Qualifier("paymentSucceededDeadLetterQueue") Queue queue,
            @Qualifier("paymentDeadLetterExchange") DirectExchange exchange,
            PaymentMessagingProperties properties
    ) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(properties.succeededDeadLetterRoutingKey());
    }

    @Bean
    Binding paymentSucceededRetryBinding(
            @Qualifier("paymentSucceededRetryQueue") Queue queue,
            @Qualifier("paymentSucceededRetryExchange") DirectExchange exchange,
            PaymentSucceededRetryProperties properties
    ) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(properties.routingKey());
    }
}
