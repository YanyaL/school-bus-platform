package com.schoolbus.booking.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Gates Booking HTTP entry points and Booking-owned messaging inside Core.
 * Cloud mode delegates those responsibilities to school-bus-booking while
 * repositories used by Transport adapters remain available.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(
        prefix = "school-bus.booking.embedded",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public @interface ConditionalOnEmbeddedBooking {
}
