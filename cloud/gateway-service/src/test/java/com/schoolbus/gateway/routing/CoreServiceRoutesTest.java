package com.schoolbus.gateway.routing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CoreServiceRoutesTest {

    @Autowired
    private RouteLocator routeLocator;

    @ParameterizedTest
    @CsvSource({
            "GET, /api/v1/trips, school-bus-transport-query-trips, lb://school-bus-transport-query",
            "GET, /api/v1/trips/11111111-1111-1111-1111-111111111111/seats, school-bus-transport-query-seats, lb://school-bus-transport-query",
            "GET, /api/v1/admin/trips, school-bus-core-api, lb://school-bus-core",
            "POST, /api/v1/admin/trips, school-bus-core-api, lb://school-bus-core",
            "GET, /api/v1/bookings, school-bus-core-api, lb://school-bus-core",
            "POST, /api/v1/accounts, school-bus-iam-accounts, lb://school-bus-iam",
            "POST, /api/v1/auth/login, school-bus-iam-auth, lb://school-bus-iam",
            "GET, /api/v1/auth/me, school-bus-iam-auth, lb://school-bus-iam",
            "POST, /api/v1/auth/refresh, school-bus-iam-auth, lb://school-bus-iam",
            "POST, /api/v1/auth/logout, school-bus-iam-auth, lb://school-bus-iam",
            "POST, /api/v1/trips, school-bus-core-api, lb://school-bus-core"
    })
    void routesRequestsToExpectedService(
            String method,
            String path,
            String expectedRouteId,
            String expectedUri
    ) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.valueOf(method), path).build()
        );

        Route route = routeLocator.getRoutes()
                .filterWhen(candidate -> Mono.from(candidate.getPredicate().apply(exchange)))
                .blockFirst();

        assertThat(route).isNotNull();
        assertThat(route.getId()).isEqualTo(expectedRouteId);
        assertThat(route.getUri()).isEqualTo(URI.create(expectedUri));
    }

    @Test
    void removesUntrustedIdentityHeadersBeforeForwarding() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/bookings")
                        .header("Authorization", "Bearer access-token")
                        .header("X-User-Id", "spoofed-user")
                        .header("X-User-Roles", "ADMIN")
                        .header("X-Authenticated-User", "spoofed-user")
                        .build()
        );

        Route route = routeLocator.getRoutes()
                .filterWhen(candidate -> Mono.from(candidate.getPredicate().apply(exchange)))
                .blockFirst();

        assertThat(route).isNotNull();
        invokeFilters(route.getFilters(), 0, exchange, filteredExchange -> {
            assertThat(filteredExchange.getRequest().getHeaders().getFirst("Authorization"))
                    .isEqualTo("Bearer access-token");
            assertThat(filteredExchange.getRequest().getHeaders().containsKey("X-User-Id")).isFalse();
            assertThat(filteredExchange.getRequest().getHeaders().containsKey("X-User-Roles")).isFalse();
            assertThat(filteredExchange.getRequest().getHeaders().containsKey("X-Authenticated-User")).isFalse();
            return filteredExchange.getResponse().setComplete();
        }).block();
    }

    private Mono<Void> invokeFilters(
            List<GatewayFilter> filters,
            int index,
            ServerWebExchange exchange,
            GatewayFilterChain terminalChain
    ) {
        if (index == filters.size()) {
            return terminalChain.filter(exchange);
        }
        return filters.get(index).filter(
                exchange,
                nextExchange -> invokeFilters(filters, index + 1, nextExchange, terminalChain)
        );
    }

    @Test
    void transportQueryRoutesCarryTimeoutMetadataAndRetryWhenEnabled() {
        MockServerWebExchange trips = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/trips").build()
        );
        Route tripsRoute = routeLocator.getRoutes()
                .filterWhen(candidate -> Mono.from(candidate.getPredicate().apply(trips)))
                .blockFirst();

        assertThat(tripsRoute).isNotNull();
        assertThat(tripsRoute.getMetadata())
                .containsEntry(org.springframework.cloud.gateway.support.RouteMetadataUtils.CONNECT_TIMEOUT_ATTR, 500)
                .containsKey(org.springframework.cloud.gateway.support.RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR);
        assertThat(tripsRoute.getFilters().stream().map(Object::toString).anyMatch(s ->
                s.contains("Retry") || s.contains("retry"))).isTrue();
    }

    @Test
    void coreRouteDoesNotIncludeRetryFilter() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/bookings").build()
        );
        Route route = routeLocator.getRoutes()
                .filterWhen(candidate -> Mono.from(candidate.getPredicate().apply(exchange)))
                .blockFirst();

        assertThat(route).isNotNull();
        assertThat(route.getId()).isEqualTo(CoreServiceRoutes.CORE_ROUTE_ID);
        assertThat(route.getFilters().stream().map(Object::toString).anyMatch(s ->
                s.contains("Retry") || s.contains("retry"))).isFalse();
    }
}
