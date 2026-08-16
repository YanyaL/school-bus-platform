package com.schoolbus.gateway.routing;

import com.schoolbus.gateway.support.CountingHttpStub;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TransportQueryRetryDisabledIntegrationTest {

    private static CountingHttpStub queryStub;
    private static CountingHttpStub coreStub;

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void registerStubs(DynamicPropertyRegistry registry) throws IOException {
        queryStub = CountingHttpStub.start();
        coreStub = CountingHttpStub.start();
        registry.add(
                "spring.cloud.discovery.client.simple.instances.school-bus-transport-query[0].uri",
                queryStub::baseUri
        );
        registry.add(
                "spring.cloud.discovery.client.simple.instances.school-bus-core[0].uri",
                coreStub::baseUri
        );
        registry.add("school-bus.gateway.transport-query-resilience.enabled", () -> "false");
        registry.add("school-bus.gateway.transport-query-resilience.retries", () -> "1");
        registry.add("school-bus.gateway.transport-query-resilience.connect-timeout", () -> "200ms");
        registry.add("school-bus.gateway.transport-query-resilience.response-timeout", () -> "800ms");
        registry.add("school-bus.gateway.transport-query-resilience.first-backoff", () -> "20ms");
        registry.add("school-bus.gateway.transport-query-resilience.max-backoff", () -> "50ms");
    }

    @AfterAll
    void stopStubs() {
        if (queryStub != null) {
            queryStub.close();
        }
        if (coreStub != null) {
            coreStub.close();
        }
    }

    @BeforeEach
    void reset() {
        queryStub.reset();
        coreStub.reset();
    }

    @Test
    void tripsGetDoesNotRetryWhenResilienceDisabled() {
        queryStub.respondSequence(
                CountingHttpStub.StubResponse.of(503, "{\"code\":\"UNAVAILABLE\"}"),
                CountingHttpStub.StubResponse.of(200, "{\"code\":\"OK\",\"data\":[]}")
        );

        webTestClient.get()
                .uri("/api/v1/trips")
                .exchange()
                .expectStatus().isEqualTo(503);

        assertThat(queryStub.invocations()).isEqualTo(1);
    }
}
