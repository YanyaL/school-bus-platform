package com.schoolbus.iamservice.config;

import com.schoolbus.iamservice.domain.identity.UserIdGenerator;
import com.schoolbus.iamservice.infrastructure.identity.SnowflakeIdGenerator;
import com.schoolbus.iamservice.infrastructure.identity.SnowflakeUserIdGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
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
        return new SnowflakeUserIdGenerator(snowflakeIdGenerator);
    }
}
