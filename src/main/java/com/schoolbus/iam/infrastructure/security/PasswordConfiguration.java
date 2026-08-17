package com.schoolbus.iam.infrastructure.security;

import com.schoolbus.iam.config.ConditionalOnEmbeddedIam;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@ConditionalOnEmbeddedIam
public class PasswordConfiguration {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
