package com.schoolbus.shared.web.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(RateLimitConfiguration.class)
                    .withPropertyValues(
                            "school-bus.rate-limit.login-qps=10",
                            "school-bus.rate-limit.create-booking-qps=30",
                            "school-bus.rate-limit.payment-callback-qps=100"
                    );

    @Test
    void shouldUseNoOpGuardWhenDisabled() {
        contextRunner
                .withPropertyValues("school-bus.rate-limit.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(TrafficGuard.class);
                    assertThat(context.getBean(TrafficGuard.class))
                            .isInstanceOf(NoOpTrafficGuard.class);
                    assertThat(context)
                            .doesNotHaveBean(SentinelRuleInitializer.class);
                });
    }

    @Test
    void shouldUseSentinelAndLoadRulesWhenEnabled() {
        contextRunner
                .withPropertyValues("school-bus.rate-limit.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(TrafficGuard.class);
                    assertThat(context.getBean(TrafficGuard.class))
                            .isInstanceOf(SentinelTrafficGuard.class);
                    assertThat(context)
                            .hasSingleBean(SentinelRuleInitializer.class);
                });
    }
}
