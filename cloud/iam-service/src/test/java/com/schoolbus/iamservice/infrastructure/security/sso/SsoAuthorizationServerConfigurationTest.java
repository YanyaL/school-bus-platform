package com.schoolbus.iamservice.infrastructure.security.sso;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import static org.assertj.core.api.Assertions.assertThat;

class SsoAuthorizationServerConfigurationTest {

    private final SsoAuthorizationServerConfiguration configuration =
            new SsoAuthorizationServerConfiguration();

    @Test
    void shouldRegisterStudentAndAdminAsPkcePublicClients() {
        SsoProperties properties = new SsoProperties(
                new SsoProperties.Client(
                        "student-web",
                        "http://127.0.0.1:5173/auth/callback",
                        "http://127.0.0.1:5173/login"
                ),
                new SsoProperties.Client(
                        "admin-web",
                        "http://127.0.0.1:5174/auth/callback",
                        "http://127.0.0.1:5174/login"
                ),
                java.util.List.of("http://127.0.0.1:5173")
        );

        RegisteredClientRepository repository =
                configuration.registeredClientRepository(properties);

        assertPublicPkceClient(
                repository.findByClientId("student-web"),
                "http://127.0.0.1:5173/auth/callback"
        );
        assertPublicPkceClient(
                repository.findByClientId("admin-web"),
                "http://127.0.0.1:5174/auth/callback"
        );
    }

    private static void assertPublicPkceClient(
            RegisteredClient client,
            String redirectUri
    ) {
        assertThat(client).isNotNull();
        assertThat(client.getClientAuthenticationMethods())
                .containsExactly(ClientAuthenticationMethod.NONE);
        assertThat(client.getAuthorizationGrantTypes())
                .containsExactly(AuthorizationGrantType.AUTHORIZATION_CODE)
                .doesNotContain(AuthorizationGrantType.REFRESH_TOKEN);
        assertThat(client.getRedirectUris()).containsExactly(redirectUri);
        assertThat(client.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(client.getScopes())
                .contains("openid", "profile", "schoolbus.read", "schoolbus.write");
    }
}
