package com.schoolbus.shared.config;

import com.schoolbus.booking.domain.order.BookingIdGenerator;
import com.schoolbus.booking.domain.order.BookingNumberGenerator;
import com.schoolbus.booking.infrastructure.identity.SnowflakeBookingIdGenerator;
import com.schoolbus.booking.infrastructure.identity.UuidBookingNumberGenerator;
import com.schoolbus.shared.domain.identity.UserIdGenerator;
import com.schoolbus.shared.infrastructure.identity.SnowflakeIdGenerator;
import com.schoolbus.shared.infrastructure.identity.SnowflakeUserIdGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IdentityConfiguration {

    @Bean
    SnowflakeIdGenerator snowflakeIdGenerator(
        @Value("${school-bus.identity.worker-id}")
        long workerId
    ) {
        return new SnowflakeIdGenerator(workerId);
    }

    @Bean
    UserIdGenerator userIdGenerator(
            SnowflakeIdGenerator snowflakeIdGenerator
    ) {
        return new SnowflakeUserIdGenerator(
                snowflakeIdGenerator
        );
    }

    @Bean
    BookingIdGenerator bookingIdGenerator(
            SnowflakeIdGenerator snowflakeIdGenerator
    ) {
        return new SnowflakeBookingIdGenerator(
                snowflakeIdGenerator
        );
    }

    @Bean
    BookingNumberGenerator bookingNumberGenerator() {
        return new UuidBookingNumberGenerator();
    }
}
