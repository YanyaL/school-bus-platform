package com.schoolbus.cdcsync.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "school-bus.cdc.canal")
public record CanalConnectionProperties(
        boolean enabled,
        String host,
        int port,
        String destination,
        String username,
        String password,
        String filter,
        int batchSize,
        Duration idleDelay,
        Duration retryDelay
) {

    public CanalConnectionProperties {
        host = requireText(host, "host");
        destination = requireText(destination, "destination");
        username = username == null ? "" : username;
        password = password == null ? "" : password;
        filter = requireText(filter, "filter");
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("port must be valid");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        idleDelay = requirePositive(idleDelay, "idleDelay");
        retryDelay = requirePositive(retryDelay, "retryDelay");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.strip();
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
