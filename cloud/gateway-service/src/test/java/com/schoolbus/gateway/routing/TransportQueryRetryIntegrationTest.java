package com.schoolbus.gateway.routing;

import com.schoolbus.gateway.support.CountingHttpStub;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TransportQueryRetryIntegrationTest {

    private static CountingHttpStub queryStub;
    private static CountingHttpStub coreStub;
    private static CountingHttpStub iamStub;

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void registerStubs(DynamicPropertyRegistry registry) throws IOException {
        queryStub = CountingHttpStub.start();
        coreStub = CountingHttpStub.start();
        iamStub = CountingHttpStub.start();
        registry.add(
                "spring.cloud.discovery.client.simple.instances.school-bus-transport-query[0].uri",
                queryStub::baseUri
        );
        registry.add(
                "spring.cloud.discovery.client.simple.instances.school-bus-core[0].uri",
                coreStub::baseUri
        );
        registry.add(
                "spring.cloud.discovery.client.simple.instances.school-bus-iam[0].uri",
                iamStub::baseUri
        );
        registry.add("school-bus.gateway.transport-query-resilience.enabled", () -> "true");
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
        if (iamStub != null) {
            iamStub.close();
        }
    }

    @BeforeEach
    void resetStubs() {
        queryStub.reset();
        coreStub.reset();
        iamStub.reset();
        webTestClient = webTestClient.mutate().responseTimeout(Duration.ofSeconds(5)).build();
    }

    @Test
    void tripsGetRetriesOnceOn503ThenSucceeds() {
        queryStub.respondSequence(
                CountingHttpStub.StubResponse.of(503, "{\"code\":\"UNAVAILABLE\"}"),
                CountingHttpStub.StubResponse.of(200, "{\"code\":\"OK\",\"data\":[]}")
        );

        webTestClient.get()
                .uri("/api/v1/trips")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo("OK");

        assertThat(queryStub.invocations()).isEqualTo(2);
        assertThat(coreStub.invocations()).isZero();
    }

    @Test
    void tripsGetStopsAfterConfiguredRetriesOnPersistent503() {
        queryStub.respondWith(attempt ->
                CountingHttpStub.StubResponse.of(503, "{\"code\":\"UNAVAILABLE\"}"));

        webTestClient.get()
                .uri("/api/v1/trips")
                .exchange()
                .expectStatus().isEqualTo(503);

        assertThat(queryStub.invocations()).isEqualTo(2);
    }

    @Test
    void tripsGetRetriesOn502() {
        queryStub.respondSequence(
                CountingHttpStub.StubResponse.of(502, "bad gateway"),
                CountingHttpStub.StubResponse.of(200, "{\"code\":\"OK\",\"data\":[]}")
        );

        webTestClient.get()
                .uri("/api/v1/trips")
                .exchange()
                .expectStatus().isOk();

        assertThat(queryStub.invocations()).isEqualTo(2);
    }

    @Test
    void tripsGetTimesOutAroundConfiguredResponseTimeout() {
        queryStub.respondWith(attempt ->
                CountingHttpStub.StubResponse.delayed(200, "{\"code\":\"OK\"}", 2500));

        long started = System.nanoTime();
        webTestClient.get()
                .uri("/api/v1/trips")
                .exchange()
                .expectStatus().isEqualTo(504);
        long elapsedMs = Duration.ofNanos(System.nanoTime() - started).toMillis();

        assertThat(elapsedMs).isLessThan(2500);
        assertThat(elapsedMs).isGreaterThanOrEqualTo(700);
        assertThat(queryStub.invocations()).isBetween(1, 2);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 429, 500})
    void tripsGetDoesNotRetryNonRetriableStatuses(int status) {
        queryStub.respondWith(attempt ->
                CountingHttpStub.StubResponse.of(status, "{\"code\":\"ERR\"}"));

        webTestClient.get()
                .uri("/api/v1/trips")
                .exchange()
                .expectStatus().isEqualTo(status);

        assertThat(queryStub.invocations()).isEqualTo(1);
    }

    @Test
    void seatsGetUsesSameRetryPolicy() {
        queryStub.respondSequence(
                CountingHttpStub.StubResponse.of(503, "{\"code\":\"UNAVAILABLE\"}"),
                CountingHttpStub.StubResponse.of(200, "{\"code\":\"OK\",\"data\":{}}")
        );

        webTestClient.get()
                .uri("/api/v1/trips/11111111-1111-1111-1111-111111111111/seats")
                .exchange()
                .expectStatus().isOk();

        assertThat(queryStub.invocations()).isEqualTo(2);
        assertThat(coreStub.invocations()).isZero();
    }

    @Test
    void coreBookingPostIsNotRetriedOn503() {
        coreStub.respondWith(attempt ->
                CountingHttpStub.StubResponse.of(503, "{\"code\":\"UNAVAILABLE\"}"));

        webTestClient.post()
                .uri("/api/v1/bookings")
                .bodyValue("{\"tripNumber\":\"11111111-1111-1111-1111-111111111111\",\"seatNumber\":\"1\"}")
                .header("Content-Type", "application/json")
                .exchange()
                .expectStatus().isEqualTo(503);

        assertThat(coreStub.invocations()).isEqualTo(1);
        assertThat(queryStub.invocations()).isZero();
    }

    @Test
    void authLoginPostIsNotRetriedOn503() {
        iamStub.respondWith(attempt ->
                CountingHttpStub.StubResponse.of(503, "{\"code\":\"UNAVAILABLE\"}"));

        webTestClient.post()
                .uri("/api/v1/auth/login")
                .bodyValue("{\"studentNumber\":\"H0000001\",\"password\":\"x\"}")
                .header("Content-Type", "application/json")
                .exchange()
                .expectStatus().isEqualTo(503);

        assertThat(iamStub.invocations()).isEqualTo(1);
        assertThat(coreStub.invocations()).isZero();
    }

    @Test
    void adminWriteIsNotRetriedOn503() {
        coreStub.respondWith(attempt ->
                CountingHttpStub.StubResponse.of(503, "{\"code\":\"UNAVAILABLE\"}"));

        webTestClient.post()
                .uri("/api/v1/admin/trips")
                .bodyValue("{}")
                .header("Content-Type", "application/json")
                .exchange()
                .expectStatus().isEqualTo(503);

        assertThat(coreStub.invocations()).isEqualTo(1);
    }
}
