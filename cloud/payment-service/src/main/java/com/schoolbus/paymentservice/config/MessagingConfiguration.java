package com.schoolbus.paymentservice.config;

import com.schoolbus.paymentservice.infrastructure.messaging.OutboxRelayProperties;
import com.schoolbus.paymentservice.infrastructure.messaging.PaymentMessagingProperties;
import com.schoolbus.paymentservice.infrastructure.messaging.RefundRetryProperties;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties({
        PaymentMessagingProperties.class,
        OutboxRelayProperties.class,
        RefundRetryProperties.class
})
public class MessagingConfiguration {

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory
    ) {
        SimpleRabbitListenerContainerFactory factory =
                new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setAcknowledgeMode(
                org.springframework.amqp.core.AcknowledgeMode.MANUAL
        );
        return factory;
    }
}
