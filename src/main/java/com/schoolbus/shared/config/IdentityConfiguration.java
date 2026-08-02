package com.schoolbus.shared.config;

import com.schoolbus.shared.domain.identity.UserIdGenerator;
import com.schoolbus.shared.infrastructure.identity.SnowflakeUserIdGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IdentityConfiguration {

    @Bean
    UserIdGenerator userIdGenerator(
        @Value("${school-bus.identity.worker-id}")
        long workerId
    ) {
        return new SnowflakeUserIdGenerator(workerId);
    }
}
