package com.schoolbus.shared.web.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfiguration {

    @Bean
    @ConditionalOnProperty(
            prefix = "school-bus.rate-limit",
            name = "enabled",
            havingValue = "true"
    )
    TrafficGuard sentinelTrafficGuard() {
        return new SentinelTrafficGuard();
    }

    @Bean
    @ConditionalOnMissingBean(TrafficGuard.class)
    TrafficGuard noOpTrafficGuard() {
        return new NoOpTrafficGuard();
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "school-bus.rate-limit",
            name = "enabled",
            havingValue = "true"
    )
    SentinelRuleInitializer sentinelRuleInitializer(
            RateLimitProperties properties
    ) {
        return new SentinelRuleInitializer(properties);
    }

    @Bean
    RateLimitInterceptor rateLimitInterceptor(TrafficGuard trafficGuard) {
        return new RateLimitInterceptor(trafficGuard);
    }

    @Bean
    WebMvcConfigurer rateLimitWebMvcConfigurer(
            RateLimitInterceptor interceptor
    ) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor);
            }
        };
    }
}
