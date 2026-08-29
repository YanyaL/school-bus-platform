package com.schoolbus.transport.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Keeps the modular-monolith Transport administration runtime enabled by
 * default and disables it when the cloud Transport Command service owns the
 * vehicle and route administration APIs.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(
        prefix = "school-bus.transport.admin.embedded",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public @interface ConditionalOnEmbeddedTransportAdmin {
}
