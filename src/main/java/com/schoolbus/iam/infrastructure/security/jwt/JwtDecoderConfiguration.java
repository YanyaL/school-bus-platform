package com.schoolbus.iam.infrastructure.security.jwt;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;

/**
 * Always provides a JwtDecoder. Prefer the embedded KeyPair when present;
 * otherwise load the public key only (cloud strangler mode).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtDecoderConfiguration {

    @Bean
    @ConditionalOnBean(KeyPair.class)
    JwtDecoder jwtDecoderFromKeyPair(
            KeyPair jwtKeyPair,
            JwtProperties properties
    ) {
        return buildDecoder((RSAPublicKey) jwtKeyPair.getPublic(), properties);
    }

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    @ConditionalOnProperty(
            prefix = "school-bus.iam.embedded",
            name = "enabled",
            havingValue = "false"
    )
    JwtDecoder jwtDecoderFromPublicKey(
            JwtProperties properties,
            ResourceLoader resourceLoader,
            @org.springframework.beans.factory.annotation.Value(
                    "${school-bus.security.jwt.public-key-location:}"
            )
            String publicKeyLocation
    ) {
        if (!StringUtils.hasText(publicKeyLocation)) {
            throw new IllegalStateException(
                    "school-bus.security.jwt.public-key-location must be configured "
                            + "when embedded IAM is disabled"
            );
        }
        try (InputStream inputStream = resourceLoader
                .getResource(publicKeyLocation)
                .getInputStream()) {
            RSAPublicKey publicKey = (RSAPublicKey)
                    RsaKeyConverters.x509().convert(inputStream);
            return buildDecoder(publicKey, properties);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to load JWT public key from " + publicKeyLocation,
                    exception
            );
        }
    }

    private static JwtDecoder buildDecoder(
            RSAPublicKey publicKey,
            JwtProperties properties
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withPublicKey(publicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();

        OAuth2TokenValidator<Jwt> standardValidator =
                JwtValidators.createDefaultWithIssuer(properties.issuer());
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> {
            if (jwt.getAudience().contains(properties.audience())) {
                return OAuth2TokenValidatorResult.success();
            }
            OAuth2Error error = new OAuth2Error(
                    "invalid_token",
                    "The required audience is missing",
                    null
            );
            return OAuth2TokenValidatorResult.failure(error);
        };

        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        standardValidator,
                        audienceValidator
                )
        );
        return decoder;
    }
}
