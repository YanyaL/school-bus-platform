package com.schoolbus.gateway.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.RetryGatewayFilterFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Records low-cardinality retry metrics for Transport Query GET routes.
 * Does not log tokens, trip numbers, or full query strings.
 */
public class TransportQueryRetryMetricsGatewayFilter implements GatewayFilter, Ordered {

    public static final String RETRY_TOTAL = "school_bus_gateway_query_retry_total";
    public static final String RETRY_EXHAUSTED = "school_bus_gateway_query_retry_exhausted_total";

    private final MeterRegistry meterRegistry;
    private final String routeId;

    public TransportQueryRetryMetricsGatewayFilter(MeterRegistry meterRegistry, String routeId) {
        this.meterRegistry = meterRegistry;
        this.routeId = routeId;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).doFinally(signalType -> record(exchange));
    }

    private void record(ServerWebExchange exchange) {
        int iteration = exchange.getAttributeOrDefault(
                RetryGatewayFilterFactory.RETRY_ITERATION_KEY,
                0
        );
        HttpStatusCode status = exchange.getResponse().getStatusCode();
        String outcome = outcomeLabel(status);

        if (iteration > 0) {
            meterRegistry.counter(
                    RETRY_TOTAL,
                    "route", routeId,
                    "outcome", outcome
            ).increment(iteration);
        }

        if (iteration > 0 && status != null && status.is5xxServerError()) {
            meterRegistry.counter(
                    RETRY_EXHAUSTED,
                    "route", routeId,
                    "outcome", outcome
            ).increment();
        }
    }

    private static String outcomeLabel(HttpStatusCode status) {
        if (status == null) {
            return "unknown";
        }
        if (status.is2xxSuccessful()) {
            return "success";
        }
        if (status.value() == 502) {
            return "bad_gateway";
        }
        if (status.value() == 503) {
            return "service_unavailable";
        }
        if (status.value() == 504) {
            return "gateway_timeout";
        }
        if (status.is4xxClientError()) {
            return "client_error";
        }
        if (status.is5xxServerError()) {
            return "server_error";
        }
        return "other";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }
}
