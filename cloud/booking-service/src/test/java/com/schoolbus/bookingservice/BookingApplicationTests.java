package com.schoolbus.bookingservice;

import com.schoolbus.bookingservice.application.booking.BookingApplicationService;
import com.schoolbus.bookingservice.application.booking.BookingCancellationApplicationService;
import com.schoolbus.bookingservice.application.booking.BookingQueryApplicationService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

@SpringBootTest(
        properties = {
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.flyway.enabled=false",
                "management.health.redis.enabled=false",
                "management.health.rabbit.enabled=false",
                "management.health.db.enabled=false",
                "school-bus.messaging.outbox-relay.enabled=false",
                "school-bus.booking.expiration.scheduler.enabled=false"
        }
)
@EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        RedisAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class,
        RabbitAutoConfiguration.class,
        org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration.class
})
@ActiveProfiles("test")
class BookingApplicationTests {

    private static Path publicKeyPath;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private BookingApplicationService bookingApplicationService;

    @MockitoBean
    private BookingQueryApplicationService bookingQueryApplicationService;

    @MockitoBean
    private BookingCancellationApplicationService bookingCancellationApplicationService;

    @BeforeAll
    static void writeTemporaryPublicKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        String body = Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(keyPair.getPublic().getEncoded());
        String pem = "-----BEGIN PUBLIC KEY-----\n"
                + body
                + "\n-----END PUBLIC KEY-----\n";
        publicKeyPath = Files.createTempFile(
                "booking-service-public-",
                ".pem"
        );
        Files.writeString(publicKeyPath, pem, StandardCharsets.US_ASCII);
        publicKeyPath.toFile().deleteOnExit();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "school-bus.security.jwt.public-key-location",
                () -> "file:" + publicKeyPath.toAbsolutePath()
        );
    }

    @Test
    void contextLoads() {
    }
}
