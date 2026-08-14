package com.schoolbus.shared.web.ratelimit;

import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SentinelRuleInitializerTest {

    @AfterEach
    void clearRules() {
        FlowRuleManager.loadRules(List.of());
    }

    @Test
    void shouldLoadConfiguredRulesForProtectedEndpoints() {
        SentinelRuleInitializer initializer = new SentinelRuleInitializer(
                new RateLimitProperties(true, 10D, 30D, 100D)
        );

        initializer.afterPropertiesSet();

        assertThat(FlowRuleManager.getRules())
                .extracting(
                        rule -> rule.getResource(),
                        rule -> rule.getCount()
                )
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(
                                RateLimitResource.LOGIN,
                                10D
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                RateLimitResource.CREATE_BOOKING,
                                30D
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                RateLimitResource.PAYMENT_CALLBACK,
                                100D
                        )
                );
    }
}
