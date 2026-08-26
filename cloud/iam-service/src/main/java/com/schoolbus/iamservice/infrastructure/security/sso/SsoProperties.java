package com.schoolbus.iamservice.infrastructure.security.sso;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "school-bus.security.sso")
public record SsoProperties(
        Client student,
        Client admin,
        List<String> allowedOrigins
) {

    public SsoProperties {
        if (student == null || admin == null) {
            throw new IllegalArgumentException(
                    "Both student and admin SSO clients must be configured"
            );
        }
        allowedOrigins = List.copyOf(allowedOrigins == null
                ? List.of()
                : allowedOrigins);
    }

    public record Client(
            String clientId,
            String redirectUri,
            String postLogoutRedirectUri
    ) {

        public Client {
            requireText(clientId, "clientId");
            requireText(redirectUri, "redirectUri");
            requireText(postLogoutRedirectUri, "postLogoutRedirectUri");
        }

        private static void requireText(String value, String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(
                        "SSO " + name + " must not be blank"
                );
            }
        }
    }
}
