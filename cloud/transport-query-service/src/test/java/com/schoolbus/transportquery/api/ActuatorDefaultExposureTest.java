package com.schoolbus.transportquery.api;

import com.schoolbus.transportquery.application.BookableTripCache;
import com.schoolbus.transportquery.application.TripQueryRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        properties = {
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.flyway.enabled=false",
                "management.health.redis.enabled=false",
                "management.endpoints.web.exposure.include=health,info"
        }
)
@EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class,
        org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration.class,
        org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration.class
})
@AutoConfigureMockMvc
class ActuatorDefaultExposureTest {

    private static Path publicKeyPath;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TripQueryRepository tripQueryRepository;

    @MockitoBean
    private BookableTripCache bookableTripCache;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeAll
    static void writeTemporaryPublicKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        String body = Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(keyPair.getPublic().getEncoded());
        String pem = "-----BEGIN PUBLIC KEY-----\n" + body + "\n-----END PUBLIC KEY-----\n";
        publicKeyPath = Files.createTempFile("transport-query-default-public-", ".pem");
        Files.writeString(publicKeyPath, pem, StandardCharsets.US_ASCII);
        publicKeyPath.toFile().deleteOnExit();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "school-bus.security.jwt.public-key-location",
                () -> publicKeyPath.toUri().toString()
        );
        registry.add("school-bus.security.jwt.issuer", () -> "https://school-bus.local");
        registry.add("school-bus.security.jwt.audience", () -> "school-bus-api");
        registry.add("school-bus.security.jwt.access-token-ttl", () -> "PT15M");
    }

    @Test
    void defaultExposureIncludesOnlyHealthAndInfo() throws Exception {
        mockMvc.perform(get("/actuator").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.health").exists())
                .andExpect(jsonPath("$._links.info").exists())
                .andExpect(jsonPath("$._links.metrics").doesNotExist());
    }

    @Test
    void healthRemainsAvailableWithoutJwt() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
