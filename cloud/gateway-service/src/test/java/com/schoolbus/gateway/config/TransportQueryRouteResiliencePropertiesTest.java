package com.schoolbus.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class TransportQueryRouteResiliencePropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(GatewayConfiguration.class);

    @Test
    void rejectsResponseTimeoutNotGreaterThanConnectTimeout() {
        contextRunner
                .withPropertyValues(
                        "school-bus.gateway.transport-query-resilience.enabled=true",
                        "school-bus.gateway.transport-query-resilience.retries=1",
                        "school-bus.gateway.transport-query-resilience.connect-timeout=2s",
                        "school-bus.gateway.transport-query-resilience.response-timeout=500ms",
                        "school-bus.gateway.transport-query-resilience.first-backoff=50ms",
                        "school-bus.gateway.transport-query-resilience.max-backoff=200ms"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsZeroConnectTimeout() {
        contextRunner
                .withPropertyValues(
                        "school-bus.gateway.transport-query-resilience.enabled=true",
                        "school-bus.gateway.transport-query-resilience.retries=1",
                        "school-bus.gateway.transport-query-resilience.connect-timeout=0ms",
                        "school-bus.gateway.transport-query-resilience.response-timeout=2s",
                        "school-bus.gateway.transport-query-resilience.first-backoff=50ms",
                        "school-bus.gateway.transport-query-resilience.max-backoff=200ms"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsEnabledWithZeroRetries() {
        contextRunner
                .withPropertyValues(
                        "school-bus.gateway.transport-query-resilience.enabled=true",
                        "school-bus.gateway.transport-query-resilience.retries=0",
                        "school-bus.gateway.transport-query-resilience.connect-timeout=500ms",
                        "school-bus.gateway.transport-query-resilience.response-timeout=2s",
                        "school-bus.gateway.transport-query-resilience.first-backoff=50ms",
                        "school-bus.gateway.transport-query-resilience.max-backoff=200ms"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void acceptsValidDefaults() {
        contextRunner
                .withPropertyValues(
                        "school-bus.gateway.transport-query-resilience.enabled=true",
                        "school-bus.gateway.transport-query-resilience.retries=1",
                        "school-bus.gateway.transport-query-resilience.connect-timeout=500ms",
                        "school-bus.gateway.transport-query-resilience.response-timeout=2s",
                        "school-bus.gateway.transport-query-resilience.first-backoff=50ms",
                        "school-bus.gateway.transport-query-resilience.max-backoff=200ms"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    TransportQueryRouteResilienceProperties props =
                            context.getBean(TransportQueryRouteResilienceProperties.class);
                    assertThat(props.retryFilterEnabled()).isTrue();
                    assertThat(props.retries()).isEqualTo(1);
                });
    }
}
