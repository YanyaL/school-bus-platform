package com.schoolbus.bookingservice.infrastructure.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("!test")
@EnableConfigurationProperties(
        BookingExpirationMessagingProperties.class
)
public class BookingExpirationRabbitConfiguration {

    @Bean
    DirectExchange bookingExpirationDelayExchange(
            BookingExpirationMessagingProperties properties
    ) {
        return new DirectExchange(
                properties.delayExchange(),
                true,
                false
        );
    }

    @Bean
    DirectExchange bookingExpirationProcessingExchange(
            BookingExpirationMessagingProperties properties
    ) {
        return new DirectExchange(
                properties.processingExchange(),
                true,
                false
        );
    }

    @Bean
    DirectExchange bookingExpirationDeadLetterExchange(
            BookingExpirationMessagingProperties properties
    ) {
        return new DirectExchange(
                properties.deadLetterExchange(),
                true,
                false
        );
    }

    @Bean
    Queue bookingExpirationDelayQueue(
            BookingExpirationMessagingProperties properties
    ) {
        return QueueBuilder
                .durable(properties.delayQueue())
                .deadLetterExchange(properties.processingExchange())
                .deadLetterRoutingKey(properties.processingRoutingKey())
                .build();
    }

    @Bean
    Queue bookingExpirationProcessingQueue(
            BookingExpirationMessagingProperties properties
    ) {
        return QueueBuilder
                .durable(properties.processingQueue())
                .deadLetterExchange(properties.deadLetterExchange())
                .deadLetterRoutingKey(properties.deadLetterRoutingKey())
                .build();
    }

    @Bean
    Queue bookingExpirationDeadLetterQueue(
            BookingExpirationMessagingProperties properties
    ) {
        return QueueBuilder
                .durable(properties.deadLetterQueue())
                .build();
    }

    @Bean
    Binding bookingExpirationDelayBinding(
            @Qualifier("bookingExpirationDelayQueue") Queue queue,
            @Qualifier("bookingExpirationDelayExchange")
            DirectExchange exchange,
            BookingExpirationMessagingProperties properties
    ) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(properties.delayRoutingKey());
    }

    @Bean
    Binding bookingExpirationProcessingBinding(
            @Qualifier("bookingExpirationProcessingQueue") Queue queue,
            @Qualifier("bookingExpirationProcessingExchange")
            DirectExchange exchange,
            BookingExpirationMessagingProperties properties
    ) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(properties.processingRoutingKey());
    }

    @Bean
    Binding bookingExpirationDeadLetterBinding(
            @Qualifier("bookingExpirationDeadLetterQueue") Queue queue,
            @Qualifier("bookingExpirationDeadLetterExchange")
            DirectExchange exchange,
            BookingExpirationMessagingProperties properties
    ) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(properties.deadLetterRoutingKey());
    }
}
