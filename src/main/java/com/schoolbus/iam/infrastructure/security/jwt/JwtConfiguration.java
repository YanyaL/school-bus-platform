package com.schoolbus.iam.infrastructure.security.jwt;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfiguration {

    @Bean
    @Profile("!prod")
    KeyPair jwtKeyPair(
            @Value("${school-bus.security.jwt.public-key-location:}")
            String publicKeyLocation,
            @Value("${school-bus.security.jwt.private-key-location:}")
            String privateKeyLocation,
            org.springframework.core.io.ResourceLoader resourceLoader
    ) {
        if (StringUtils.hasText(publicKeyLocation)
                && StringUtils.hasText(privateKeyLocation)) {
            return loadKeyPair(
                    resourceLoader.getResource(publicKeyLocation),
                    resourceLoader.getResource(privateKeyLocation)
            );
        }
        try {
            KeyPairGenerator generator =
                    KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "RSA algorithm is not available",
                    exception
            );
        }
    }

    private static KeyPair loadKeyPair(
            Resource publicKeyResource,
            Resource privateKeyResource
    ) {
        try (
                InputStream publicKeyInput =
                        publicKeyResource.getInputStream();
                InputStream privateKeyInput =
                        privateKeyResource.getInputStream()
        ) {
            RSAPublicKey publicKey = (RSAPublicKey)
                    RsaKeyConverters.x509().convert(publicKeyInput);
            RSAPrivateKey privateKey = (RSAPrivateKey)
                    RsaKeyConverters.pkcs8().convert(privateKeyInput);
            return new KeyPair(publicKey, privateKey);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to load shared JWT key pair for non-prod cloud",
                    exception
            );
        }
    }

    @Bean
    JwtEncoder jwtEncoder(KeyPair jwtKeyPair) {
        RSAPublicKey publicKey =
                (RSAPublicKey) jwtKeyPair.getPublic();
        RSAPrivateKey privateKey =
                (RSAPrivateKey) jwtKeyPair.getPrivate();

        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(UUID.randomUUID().toString())
                .build();
        JWKSource<SecurityContext> jwkSource =
                new ImmutableJWKSet<>(new JWKSet(rsaKey));

        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    JwtDecoder jwtDecoder(
            KeyPair jwtKeyPair,
            JwtProperties properties
    ) {
        RSAPublicKey publicKey =
                (RSAPublicKey) jwtKeyPair.getPublic();
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withPublicKey(publicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();

        OAuth2TokenValidator<Jwt> standardValidator =
                JwtValidators.createDefaultWithIssuer(
                        properties.issuer()
                );
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
