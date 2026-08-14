package com.schoolbus.transport.infrastructure.messaging;

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

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TripCancellationMessagingProperties.class)
public class TripCancellationRabbitConfiguration {

    @Bean
    TopicExchange tripCancellationExchange(TripCancellationMessagingProperties p) {
        return new TopicExchange(p.exchange(), true, false);
    }

    @Bean
    DirectExchange tripCancellationDeadLetterExchange(
            TripCancellationMessagingProperties p
    ) {
        return new DirectExchange(p.deadLetterExchange(), true, false);
    }

    @Bean
    Queue tripCancellationRequestedQueue(TripCancellationMessagingProperties p) {
        return businessQueue(p.requestedQueue(), p);
    }

    @Bean
    Queue tripCancellationSettledQueue(TripCancellationMessagingProperties p) {
        return businessQueue(p.settledQueue(), p);
    }

    @Bean
    Queue tripCancellationDeadLetterQueue(TripCancellationMessagingProperties p) {
        return QueueBuilder.durable(p.deadLetterQueue()).build();
    }

    @Bean
    Binding tripCancellationRequestedBinding(
            @Qualifier("tripCancellationRequestedQueue") Queue queue,
            @Qualifier("tripCancellationExchange") TopicExchange exchange,
            TripCancellationMessagingProperties p
    ) {
        return BindingBuilder.bind(queue).to(exchange).with(p.requestedRoutingKey());
    }

    @Bean
    Binding tripCancellationSettledBinding(
            @Qualifier("tripCancellationSettledQueue") Queue queue,
            @Qualifier("tripCancellationExchange") TopicExchange exchange,
            TripCancellationMessagingProperties p
    ) {
        return BindingBuilder.bind(queue).to(exchange).with(p.settledRoutingKey());
    }

    @Bean
    Binding tripCancellationDeadLetterBinding(
            @Qualifier("tripCancellationDeadLetterQueue") Queue queue,
            @Qualifier("tripCancellationDeadLetterExchange") DirectExchange exchange,
            TripCancellationMessagingProperties p
    ) {
        return BindingBuilder.bind(queue).to(exchange).with(p.deadLetterRoutingKey());
    }

    private Queue businessQueue(
            String name,
            TripCancellationMessagingProperties p
    ) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(p.deadLetterExchange())
                .deadLetterRoutingKey(p.deadLetterRoutingKey())
                .build();
    }
}
