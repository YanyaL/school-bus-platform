package com.schoolbus.payment.infrastructure.messaging;

import com.schoolbus.booking.infrastructure.messaging.PaymentSucceededRetryProperties;
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
        OutboxRelayProperties.class,
        RefundRetryProperties.class,
        PaymentSucceededRetryProperties.class
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
    DirectExchange paymentRefundRetryExchange(
            RefundRetryProperties properties
    ) {
        return new DirectExchange(
                properties.exchange(),
                true,
                false
        );
    }

    @Bean
    DirectExchange paymentSucceededRetryExchange(
            PaymentSucceededRetryProperties properties
    ) {
        return new DirectExchange(
                properties.exchange(),
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
    Queue paymentSucceededQueue(
            PaymentMessagingProperties properties
    ) {
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
    Queue paymentRefundRetryQueue(
            RefundRetryProperties retryProperties,
            PaymentMessagingProperties paymentProperties
    ) {
        return QueueBuilder
                .durable(retryProperties.queue())
                .ttl(Math.toIntExact(
                        retryProperties.delay().toMillis()
                ))
                .deadLetterExchange(paymentProperties.exchange())
                .deadLetterRoutingKey(
                        paymentProperties.refundRoutingKey()
                )
                .build();
    }

    @Bean
    Queue paymentSucceededRetryQueue(
            PaymentSucceededRetryProperties retryProperties,
            PaymentMessagingProperties paymentProperties
    ) {
        return QueueBuilder
                .durable(retryProperties.queue())
                .ttl(Math.toIntExact(
                        retryProperties.delay().toMillis()
                ))
                .deadLetterExchange(paymentProperties.exchange())
                .deadLetterRoutingKey(
                        paymentProperties.succeededRoutingKey()
                )
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

    @Bean
    Binding paymentSucceededBinding(
            @Qualifier("paymentSucceededQueue")
            Queue paymentSucceededQueue,
            @Qualifier("paymentEventsExchange")
            TopicExchange paymentEventsExchange,
            PaymentMessagingProperties properties
    ) {
        return BindingBuilder
                .bind(paymentSucceededQueue)
                .to(paymentEventsExchange)
                .with(properties.succeededRoutingKey());
    }

    @Bean
    Binding paymentSucceededDeadLetterBinding(
            @Qualifier("paymentSucceededDeadLetterQueue")
            Queue paymentSucceededDeadLetterQueue,
            @Qualifier("paymentDeadLetterExchange")
            DirectExchange paymentDeadLetterExchange,
            PaymentMessagingProperties properties
    ) {
        return BindingBuilder
                .bind(paymentSucceededDeadLetterQueue)
                .to(paymentDeadLetterExchange)
                .with(properties.succeededDeadLetterRoutingKey());
    }

    @Bean
    Binding paymentRefundRetryBinding(
            @Qualifier("paymentRefundRetryQueue")
            Queue paymentRefundRetryQueue,
            @Qualifier("paymentRefundRetryExchange")
            DirectExchange paymentRefundRetryExchange,
            RefundRetryProperties properties
    ) {
        return BindingBuilder
                .bind(paymentRefundRetryQueue)
                .to(paymentRefundRetryExchange)
                .with(properties.routingKey());
    }

    @Bean
    Binding paymentSucceededRetryBinding(
            @Qualifier("paymentSucceededRetryQueue")
            Queue paymentSucceededRetryQueue,
            @Qualifier("paymentSucceededRetryExchange")
            DirectExchange paymentSucceededRetryExchange,
            PaymentSucceededRetryProperties properties
    ) {
        return BindingBuilder
                .bind(paymentSucceededRetryQueue)
                .to(paymentSucceededRetryExchange)
                .with(properties.routingKey());
    }
}
