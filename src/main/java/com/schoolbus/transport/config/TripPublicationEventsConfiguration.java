package com.schoolbus.transport.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolbus.payment.infrastructure.outbox.OutboxMapper;
import com.schoolbus.transport.application.trip.TripPublicationOutboxPort;
import com.schoolbus.transport.infrastructure.messaging.TripPublicationMessagingProperties;
import com.schoolbus.transport.infrastructure.outbox.MyBatisTripPublicationOutbox;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Objects;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TripPublicationMessagingProperties.class)
public class TripPublicationEventsConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "school-bus.transport.publication-events",
            name = "enabled", havingValue = "false", matchIfMissing = true)
    TripPublicationOutboxPort disabledTripPublicationOutbox() {
        return event -> Objects.requireNonNull(event, "event must not be null");
    }

    @Bean
    @ConditionalOnProperty(prefix = "school-bus.transport.publication-events",
            name = "enabled", havingValue = "true")
    TripPublicationOutboxPort tripPublicationOutbox(OutboxMapper mapper, ObjectMapper objectMapper) {
        return new MyBatisTripPublicationOutbox(mapper, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "school-bus.transport.publication-events",
            name = "enabled", havingValue = "true")
    Declarables tripPublicationTopology(TripPublicationMessagingProperties properties) {
        TopicExchange exchange = new TopicExchange(properties.exchange(), true, false);
        // No business consumer in this phase. Reject overflow rather than silently discarding evidence.
        Queue queue = QueueBuilder.durable(properties.shadowQueue())
                .maxLength(properties.maximumQueueLength())
                .overflow(QueueBuilder.Overflow.rejectPublish).build();
        return new Declarables(exchange, queue,
                BindingBuilder.bind(queue).to(exchange).with(properties.routingKey()));
    }
}
