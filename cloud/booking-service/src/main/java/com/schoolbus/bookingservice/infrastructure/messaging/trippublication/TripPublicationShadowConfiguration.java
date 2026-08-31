package com.schoolbus.bookingservice.infrastructure.messaging.trippublication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolbus.bookingservice.application.trippublication.*;
import com.schoolbus.bookingservice.infrastructure.persistence.trippublication.*;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.retry.policy.SimpleRetryPolicy;
import java.time.Clock;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "school-bus.booking.trip-publication-shadow", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(TripPublicationShadowProperties.class)
public class TripPublicationShadowConfiguration {
    @Bean
    TripPublicationShadowStore tripPublicationShadowStore(TripPublicationShadowMapper mapper) {
        return new MyBatisTripPublicationShadowStore(mapper);
    }
    @Bean
    TripPublicationShadowTransaction tripPublicationShadowTransaction(TripPublicationShadowStore store, Clock clock) {
        return new TripPublicationShadowTransaction(store, clock);
    }
    @Bean
    TripPublicationMessageDecoder tripPublicationMessageDecoder(ObjectMapper mapper) { return new TripPublicationMessageDecoder(mapper); }
    @Bean
    TripPublicationShadowListener tripPublicationShadowListener(TripPublicationMessageDecoder decoder, TripPublicationShadowTransaction transaction,
            io.micrometer.core.instrument.MeterRegistry metrics) {
        return new TripPublicationShadowListener(decoder, transaction, metrics);
    }
    @Bean
    Declarables tripPublicationShadowTopology(TripPublicationShadowProperties p) {
        TopicExchange exchange = new TopicExchange(p.exchange(), true, false);
        DirectExchange dead = new DirectExchange(p.deadLetterExchange(), true, false);
        Queue queue = QueueBuilder.durable(p.queue()).maxLength(10000).overflow(QueueBuilder.Overflow.rejectPublish)
                .deadLetterExchange(p.deadLetterExchange()).deadLetterRoutingKey("rejected").build();
        Queue dlq = QueueBuilder.durable(p.deadLetterQueue()).build();
        return new Declarables(exchange, dead, queue, dlq, BindingBuilder.bind(queue).to(exchange).with(p.routingKey()),
                BindingBuilder.bind(dlq).to(dead).with("rejected"));
    }
    @Bean
    RetryOperationsInterceptor tripPublicationShadowRetryAdvice(TripPublicationShadowProperties p) {
        var retry = new org.springframework.retry.support.RetryTemplate();
        retry.setRetryPolicy(retryPolicy(p));
        var backoff = new org.springframework.retry.backoff.FixedBackOffPolicy();
        backoff.setBackOffPeriod(p.retryDelayMs());
        retry.setBackOffPolicy(backoff);
        return RetryInterceptorBuilder.stateless().retryOperations(retry)
                .recoverer(new RejectAndDontRequeueRecoverer()).build();
    }
    static SimpleRetryPolicy retryPolicy(TripPublicationShadowProperties p) {
        return new SimpleRetryPolicy(p.maximumAttempts(), Map.of(TransientDataAccessException.class, true,
                DataAccessResourceFailureException.class, true), true, false);
    }
    @Bean
    SimpleRabbitListenerContainerFactory tripPublicationShadowContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory,
            RetryOperationsInterceptor tripPublicationShadowRetryAdvice) {
        var factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setDefaultRequeueRejected(false);
        factory.setPrefetchCount(5);
        factory.setConcurrentConsumers(1);
        factory.setAdviceChain(tripPublicationShadowRetryAdvice);
        return factory;
    }
}
