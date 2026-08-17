package com.schoolbus.iam.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "school-bus.iam.embedded")
public record EmbeddedIamProperties(
        boolean enabled
) {
}
