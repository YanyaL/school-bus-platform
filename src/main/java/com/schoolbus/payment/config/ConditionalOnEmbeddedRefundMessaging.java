package com.schoolbus.payment.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Gates refund Outbox relay and RabbitMQ consumer inside Core. Cloud mode
 * delegates those responsibilities to school-bus-payment.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(
        prefix = "school-bus.payment.refund-messaging",
        name = "embedded",
        havingValue = "true",
        matchIfMissing = true
)
public @interface ConditionalOnEmbeddedRefundMessaging {
}
