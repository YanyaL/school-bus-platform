package com.schoolbus.iamservice.infrastructure.security.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "school-bus.security.admin-bootstrap")
public record AdminBootstrapProperties(
        boolean enabled,
        String studentNumber
) {

    public AdminBootstrapProperties {
        studentNumber = studentNumber == null ? "" : studentNumber.strip();
        if (enabled && studentNumber.isBlank()) {
            throw new IllegalArgumentException(
                    "admin bootstrap studentNumber must be configured when enabled"
            );
        }
    }
}
