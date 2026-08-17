package com.schoolbus.iam.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Gates the embedded IAM vertical (register/login/session/signing).
 * Disabled on the cloud profile when school-bus-iam owns those routes.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(
        prefix = "school-bus.iam.embedded",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public @interface ConditionalOnEmbeddedIam {
}
