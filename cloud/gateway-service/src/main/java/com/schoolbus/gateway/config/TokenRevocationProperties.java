package com.schoolbus.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "school-bus.gateway.token-revocation")
public record TokenRevocationProperties(
        boolean enabled,
        boolean failClosed,
        String keyPrefix
) {

    public TokenRevocationProperties {
        if (keyPrefix == null || keyPrefix.isBlank()) {
            throw new IllegalArgumentException(
                    "token revocation keyPrefix must not be blank"
            );
        }
    }
}
