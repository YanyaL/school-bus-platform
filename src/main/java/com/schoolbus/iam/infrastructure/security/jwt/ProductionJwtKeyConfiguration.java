package com.schoolbus.iam.infrastructure.security.jwt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@Configuration(proxyBeanMethods = false)
@Profile("prod")
public class ProductionJwtKeyConfiguration {

    @Bean
    KeyPair jwtKeyPair(
            @Value("${school-bus.security.jwt.public-key-location}")
            Resource publicKeyResource,
            @Value("${school-bus.security.jwt.private-key-location}")
            Resource privateKeyResource
    ) {
        try (
                InputStream publicKeyInput =
                        publicKeyResource.getInputStream();
                InputStream privateKeyInput =
                        privateKeyResource.getInputStream()
        ) {
            RSAPublicKey publicKey = (RSAPublicKey)
                    RsaKeyConverters.x509()
                            .convert(publicKeyInput);
            RSAPrivateKey privateKey = (RSAPrivateKey)
                    RsaKeyConverters.pkcs8()
                            .convert(privateKeyInput);
            return new KeyPair(publicKey, privateKey);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to load production JWT key pair",
                    exception
            );
        }
    }
}
