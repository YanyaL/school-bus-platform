package com.schoolbus.payment.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Gates payment callback handling inside Core. Cloud mode delegates the public
 * callback route to school-bus-payment while local monolith mode keeps it.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(
        prefix = "school-bus.payment.embedded",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public @interface ConditionalOnEmbeddedPayment {
}
