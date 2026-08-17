package com.schoolbus.paymentservice.config;

import com.schoolbus.paymentservice.infrastructure.identity.SnowflakeIdGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class PaymentConfiguration {

    @Bean
    Clock paymentClock() {
        return Clock.systemUTC();
    }

    @Bean
    SnowflakeIdGenerator paymentIdGenerator(
            Clock clock,
            @Value("${school-bus.identity.worker-id:2}") long workerId
    ) {
        return new SnowflakeIdGenerator(workerId, clock);
    }
}
