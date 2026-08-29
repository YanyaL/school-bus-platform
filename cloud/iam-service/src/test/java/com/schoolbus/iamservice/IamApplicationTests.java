package com.schoolbus.iamservice;

import com.schoolbus.iamservice.application.authentication.AccessTokenRevocationRepository;
import com.schoolbus.iamservice.infrastructure.persistence.MyBatisAccountRepository;
import com.schoolbus.iamservice.infrastructure.session.RedisLoginSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

@SpringBootTest(
        properties = {
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.flyway.enabled=false",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                        + "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
        }
)
class IamApplicationTests {

    private static final Path PUBLIC_KEY_PATH;
    private static final Path PRIVATE_KEY_PATH;

    static {
        try {
            KeyPair keyPair = KeyPairGenerator.getInstance("RSA")
                    .generateKeyPair();
            Path dir = Files.createTempDirectory("iam-jwt-");
            PUBLIC_KEY_PATH = dir.resolve("public.pem");
            PRIVATE_KEY_PATH = dir.resolve("private.pem");
            writePublicKey(PUBLIC_KEY_PATH, (RSAPublicKey) keyPair.getPublic());
            writePrivateKey(PRIVATE_KEY_PATH, (RSAPrivateKey) keyPair.getPrivate());
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @DynamicPropertySource
    static void registerJwtKeys(DynamicPropertyRegistry registry) {
        registry.add(
                "school-bus.security.jwt.public-key-location",
                () -> "file:" + PUBLIC_KEY_PATH.toAbsolutePath()
        );
        registry.add(
                "school-bus.security.jwt.private-key-location",
                () -> "file:" + PRIVATE_KEY_PATH.toAbsolutePath()
        );
    }

    @MockitoBean
    private MyBatisAccountRepository myBatisAccountRepository;

    @MockitoBean
    private RedisLoginSessionRepository redisLoginSessionRepository;

    @MockitoBean
    private AccessTokenRevocationRepository accessTokenRevocationRepository;

    @Test
    void contextLoads() {
    }

    private static void writePublicKey(Path path, RSAPublicKey publicKey)
            throws Exception {
        String pem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(publicKey.getEncoded())
                + "\n-----END PUBLIC KEY-----\n";
        Files.writeString(path, pem);
    }

    private static void writePrivateKey(Path path, RSAPrivateKey privateKey)
            throws Exception {
        String pem = "-----BEGIN " + "PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(privateKey.getEncoded())
                + "\n-----END " + "PRIVATE KEY-----\n";
        Files.writeString(path, pem);
    }
}
