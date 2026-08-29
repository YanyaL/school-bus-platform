package com.schoolbus.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "school-bus.security.jwt")
public record GatewayJwtProperties(
        String issuer,
        String audience,
        String publicKeyLocation
) {

    public GatewayJwtProperties {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("JWT issuer must not be blank");
        }
        if (audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("JWT audience must not be blank");
        }
    }
}
