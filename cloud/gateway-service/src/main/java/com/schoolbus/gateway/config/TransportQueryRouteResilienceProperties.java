package com.schoolbus.gateway.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "school-bus.gateway.transport-query-resilience")
public record TransportQueryRouteResilienceProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("1") @Min(0) @Max(3) int retries,
        @DefaultValue("500ms") @NotNull Duration connectTimeout,
        @DefaultValue("2s") @NotNull Duration responseTimeout,
        @DefaultValue("50ms") @NotNull Duration firstBackoff,
        @DefaultValue("200ms") @NotNull Duration maxBackoff
) {

    @AssertTrue(message = "connect-timeout must be positive")
    public boolean isConnectTimeoutPositive() {
        return connectTimeout != null && !connectTimeout.isNegative() && !connectTimeout.isZero();
    }

    @AssertTrue(message = "response-timeout must be positive")
    public boolean isResponseTimeoutPositive() {
        return responseTimeout != null && !responseTimeout.isNegative() && !responseTimeout.isZero();
    }

    @AssertTrue(message = "first-backoff must be positive")
    public boolean isFirstBackoffPositive() {
        return firstBackoff != null && !firstBackoff.isNegative() && !firstBackoff.isZero();
    }

    @AssertTrue(message = "max-backoff must be positive")
    public boolean isMaxBackoffPositive() {
        return maxBackoff != null && !maxBackoff.isNegative() && !maxBackoff.isZero();
    }

    @AssertTrue(message = "response-timeout must be greater than connect-timeout")
    public boolean isResponseTimeoutGreaterThanConnectTimeout() {
        return connectTimeout != null
                && responseTimeout != null
                && responseTimeout.compareTo(connectTimeout) > 0;
    }

    @AssertTrue(message = "max-backoff must be greater than or equal to first-backoff")
    public boolean isMaxBackoffAtLeastFirstBackoff() {
        return firstBackoff != null
                && maxBackoff != null
                && maxBackoff.compareTo(firstBackoff) >= 0;
    }

    @AssertTrue(message = "when resilience is enabled, retries must be between 1 and 3")
    public boolean isRetriesValidWhenEnabled() {
        return !enabled || (retries >= 1 && retries <= 3);
    }

    public boolean retryFilterEnabled() {
        return enabled && retries > 0;
    }
}
