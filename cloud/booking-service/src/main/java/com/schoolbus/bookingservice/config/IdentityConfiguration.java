package com.schoolbus.bookingservice.config;

import com.schoolbus.bookingservice.domain.order.BookingIdGenerator;
import com.schoolbus.bookingservice.domain.order.BookingNumberGenerator;
import com.schoolbus.bookingservice.infrastructure.identity.SnowflakeBookingIdGenerator;
import com.schoolbus.bookingservice.infrastructure.identity.UuidBookingNumberGenerator;
import com.schoolbus.bookingservice.shared.infrastructure.identity.SnowflakeIdGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class IdentityConfiguration {

    @Bean
    SnowflakeIdGenerator snowflakeIdGenerator(
            @Value("${school-bus.identity.worker-id:3}") long workerId
    ) {
        return new SnowflakeIdGenerator(workerId);
    }

    @Bean
    BookingIdGenerator bookingIdGenerator(
            SnowflakeIdGenerator snowflakeIdGenerator
    ) {
        return new SnowflakeBookingIdGenerator(snowflakeIdGenerator);
    }

    @Bean
    BookingNumberGenerator bookingNumberGenerator() {
        return new UuidBookingNumberGenerator();
    }
}
