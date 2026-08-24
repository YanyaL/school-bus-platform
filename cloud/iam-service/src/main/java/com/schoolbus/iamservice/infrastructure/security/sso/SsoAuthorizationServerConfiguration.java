package com.schoolbus.iamservice.infrastructure.security.sso;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.schoolbus.iamservice.infrastructure.security.jwt.JwtProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SsoProperties.class)
public class SsoAuthorizationServerConfiguration {

    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerSecurityFilterChain(
            HttpSecurity http,
            @Qualifier("ssoCorsConfigurationSource")
            CorsConfigurationSource corsConfigurationSource
    ) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServer =
                OAuth2AuthorizationServerConfigurer.authorizationServer();

        http
                .securityMatcher(request ->
                        authorizationServer.getEndpointsMatcher()
                                .matches(request)
                                || isSsoPreflightRequest(request)
                )
                .cors(cors -> cors.configurationSource(
                        corsConfigurationSource
                ))
                .with(authorizationServer, server -> server
                        .oidc(Customizer.withDefaults())
                )
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(
                                new LoginUrlAuthenticationEntryPoint("/login")
                        )
                );

        return http.build();
    }

    private static boolean isSsoPreflightRequest(
            HttpServletRequest request
    ) {
        if (!CorsUtils.isPreFlightRequest(request)) {
            return false;
        }
        String path = request.getRequestURI()
                .substring(request.getContextPath().length());
        return path.startsWith("/oauth2/")
                || path.startsWith("/.well-known/");
    }

    @Bean
    CorsConfigurationSource ssoCorsConfigurationSource(
            SsoProperties properties
    ) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Accept",
                "Content-Type",
                "Origin"
        ));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/oauth2/**", configuration);
        source.registerCorsConfiguration("/.well-known/**", configuration);
        return source;
    }

    @Bean
    RegisteredClientRepository registeredClientRepository(
            SsoProperties properties
    ) {
        return new InMemoryRegisteredClientRepository(
                publicClient(properties.student()),
                publicClient(properties.admin())
        );
    }

    private static RegisteredClient publicClient(
            SsoProperties.Client properties
    ) {
        return RegisteredClient
                .withId(properties.clientId())
                .clientId(properties.clientId())
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(properties.redirectUri())
                .postLogoutRedirectUri(properties.postLogoutRedirectUri())
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope("schoolbus.read")
                .scope("schoolbus.write")
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(15))
                        .build())
                .build();
    }

    @Bean
    AuthorizationServerSettings authorizationServerSettings(
            JwtProperties jwtProperties
    ) {
        return AuthorizationServerSettings.builder()
                .issuer(jwtProperties.issuer())
                .build();
    }

    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> ssoTokenCustomizer(
            JwtProperties jwtProperties
    ) {
        return context -> {
            if (!(context.getPrincipal().getPrincipal()
                    instanceof SchoolBusUserPrincipal principal)) {
                return;
            }
            context.getClaims()
                    .subject(Long.toString(principal.userId()))
                    .claim("student_number", principal.studentNumber())
                    .claim("roles", principal.roles());

            if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                context.getClaims().audience(
                        List.of(jwtProperties.audience())
                );
            }
        };
    }

    @Bean
    JwtDecoder authorizationServerJwtDecoder(
            JWKSource<SecurityContext> jwtJwkSource
    ) {
        return org.springframework.security.oauth2.server.authorization
                .config.annotation.web.configuration
                .OAuth2AuthorizationServerConfiguration
                .jwtDecoder(jwtJwkSource);
    }
}
