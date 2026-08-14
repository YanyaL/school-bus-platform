package com.schoolbus.shared.web.ratelimit;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.util.List;
import java.util.Objects;

public class SentinelRuleInitializer
        implements InitializingBean, DisposableBean {

    private final RateLimitProperties properties;

    public SentinelRuleInitializer(RateLimitProperties properties) {
        this.properties = Objects.requireNonNull(properties);
    }

    @Override
    public void afterPropertiesSet() {
        FlowRuleManager.loadRules(List.of(
                qpsRule(
                        RateLimitResource.LOGIN,
                        properties.loginQps()
                ),
                qpsRule(
                        RateLimitResource.CREATE_BOOKING,
                        properties.createBookingQps()
                ),
                qpsRule(
                        RateLimitResource.PAYMENT_CALLBACK,
                        properties.paymentCallbackQps()
                )
        ));
    }

    @Override
    public void destroy() {
        FlowRuleManager.loadRules(List.of());
    }

    private FlowRule qpsRule(String resource, double qps) {
        FlowRule rule = new FlowRule(resource);
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(qps);
        rule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
        return rule;
    }
}
