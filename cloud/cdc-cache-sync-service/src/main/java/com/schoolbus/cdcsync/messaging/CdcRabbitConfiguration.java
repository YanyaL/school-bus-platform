package com.schoolbus.cdcsync.messaging;

import com.schoolbus.cdcsync.config.CdcMessagingProperties;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CdcRabbitConfiguration {

    @Bean
    DirectExchange cdcExchange(CdcMessagingProperties properties) {
        return new DirectExchange(properties.exchange(), true, false);
    }

    @Bean
    Queue tripCacheInvalidationQueue(CdcMessagingProperties properties) {
        return new Queue(properties.tripQueue(), true);
    }

    @Bean
    Queue consumedEventProjectionQueue(CdcMessagingProperties properties) {
        return new Queue(properties.consumedEventQueue(), true);
    }

    @Bean
    Binding tripCacheInvalidationBinding(
            Queue tripCacheInvalidationQueue,
            DirectExchange cdcExchange,
            CdcMessagingProperties properties
    ) {
        return BindingBuilder.bind(tripCacheInvalidationQueue)
                .to(cdcExchange)
                .with(properties.tripRoutingKey());
    }

    @Bean
    Binding consumedEventProjectionBinding(
            Queue consumedEventProjectionQueue,
            DirectExchange cdcExchange,
            CdcMessagingProperties properties
    ) {
        return BindingBuilder.bind(consumedEventProjectionQueue)
                .to(cdcExchange)
                .with(properties.consumedEventRoutingKey());
    }
}
