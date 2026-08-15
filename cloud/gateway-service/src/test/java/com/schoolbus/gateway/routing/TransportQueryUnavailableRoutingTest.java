package com.schoolbus.gateway.routing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class TransportQueryUnavailableRoutingTest {

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void registerDiscovery(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.cloud.discovery.client.simple.instances.school-bus-core[0].uri",
                () -> "http://127.0.0.1:65530"
        );
        registry.add(
                "school-bus.gateway.transport-query-service-id",
                () -> "school-bus-transport-query"
        );
        registry.add(
                "school-bus.gateway.core-service-id",
                () -> "school-bus-core"
        );
    }

    @Test
    void returnsServiceUnavailableWhenTransportQueryHasNoInstances() {
        webTestClient.get()
                .uri("/api/v1/trips")
                .exchange()
                .expectStatus()
                .isEqualTo(503);
    }
}
