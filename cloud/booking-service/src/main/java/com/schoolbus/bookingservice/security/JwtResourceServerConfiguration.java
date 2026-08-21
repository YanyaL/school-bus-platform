package com.schoolbus.bookingservice.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
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
import java.security.interfaces.RSAPublicKey;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtResourceServerConfiguration {

    @Bean
    JwtDecoder jwtDecoder(
            JwtProperties properties,
            ResourceLoader resourceLoader
    ) {
        RSAPublicKey publicKey = loadPublicKey(properties, resourceLoader);
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

    private static RSAPublicKey loadPublicKey(
            JwtProperties properties,
            ResourceLoader resourceLoader
    ) {
        if (!StringUtils.hasText(properties.publicKeyLocation())) {
            throw new IllegalStateException(
                    "school-bus.security.jwt.public-key-location must be configured; "
                            + "booking-service only verifies tokens with the core public key"
            );
        }
        Resource resource = resourceLoader.getResource(properties.publicKeyLocation());
        try (InputStream inputStream = resource.getInputStream()) {
            return (RSAPublicKey) RsaKeyConverters.x509().convert(inputStream);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to load JWT public key from "
                            + properties.publicKeyLocation(),
                    exception
            );
        }
    }
}
