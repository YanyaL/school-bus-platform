package com.schoolbus.gateway.routing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
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

    @Test
    void routesApiRequestsThroughServiceDiscovery() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/trips").build()
        );

        Route route = routeLocator.getRoutes()
                .filterWhen(candidate -> Mono.from(candidate.getPredicate().apply(exchange)))
                .blockFirst();

        assertThat(route).isNotNull();
        assertThat(route.getId()).isEqualTo("school-bus-core-api");
        assertThat(route.getUri()).isEqualTo(URI.create("lb://school-bus-core"));
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
}
