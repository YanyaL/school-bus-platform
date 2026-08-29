package com.schoolbus.gateway.security;

import com.schoolbus.gateway.config.GatewayJwtProperties;
import com.schoolbus.gateway.config.TokenRevocationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPublicKey;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "school-bus.gateway.token-revocation",
        name = "enabled",
        havingValue = "true"
)
@EnableConfigurationProperties({
        GatewayJwtProperties.class,
        TokenRevocationProperties.class
})
public class TokenRevocationConfiguration {

    @Bean
    ReactiveJwtDecoder gatewayJwtDecoder(
            GatewayJwtProperties properties,
            ResourceLoader resourceLoader
    ) {
        RSAPublicKey publicKey = loadPublicKey(properties, resourceLoader);
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
                .withPublicKey(publicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        OAuth2TokenValidator<Jwt> standardValidator =
                JwtValidators.createDefaultWithIssuer(properties.issuer());
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> {
            if (jwt.getAudience().contains(properties.audience())) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error(
                            "invalid_token",
                            "The required audience is missing",
                            null
                    )
            );
        };
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        standardValidator,
                        audienceValidator
                )
        );
        return decoder;
    }

    @Bean
    AccessTokenRevocationStore accessTokenRevocationStore(
            ReactiveStringRedisTemplate redisTemplate,
            TokenRevocationProperties properties
    ) {
        return new ReactiveRedisAccessTokenRevocationStore(
                redisTemplate,
                properties
        );
    }

    private static RSAPublicKey loadPublicKey(
            GatewayJwtProperties properties,
            ResourceLoader resourceLoader
    ) {
        if (!StringUtils.hasText(properties.publicKeyLocation())) {
            throw new IllegalStateException(
                    "school-bus.security.jwt.public-key-location must be configured "
                            + "when Gateway token revocation is enabled"
            );
        }
        Resource resource = resourceLoader.getResource(
                properties.publicKeyLocation()
        );
        try (InputStream inputStream = resource.getInputStream()) {
            return (RSAPublicKey) RsaKeyConverters.x509()
                    .convert(inputStream);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to load Gateway JWT public key from "
                            + properties.publicKeyLocation(),
                    exception
            );
        }
    }
}
