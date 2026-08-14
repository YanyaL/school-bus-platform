package com.schoolbus.shared.web.ratelimit;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SentinelTrafficGuardTest {

    private static final String RESOURCE = "test:rate-limit:blocked";

    @AfterEach
    void clearRules() {
        FlowRuleManager.loadRules(List.of());
    }

    @Test
    void shouldAllowResourceWithoutRule() {
        SentinelTrafficGuard guard = new SentinelTrafficGuard();

        assertThatCode(() -> guard.acquire("test:unlimited").close())
                .doesNotThrowAnyException();
    }

    @Test
    void shouldTranslateSentinelBlockIntoBusinessException() {
        FlowRule rule = new FlowRule(RESOURCE);
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(0D);
        FlowRuleManager.loadRules(List.of(rule));
        SentinelTrafficGuard guard = new SentinelTrafficGuard();

        assertThatThrownBy(() -> guard.acquire(RESOURCE))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining(RESOURCE);
    }
}
