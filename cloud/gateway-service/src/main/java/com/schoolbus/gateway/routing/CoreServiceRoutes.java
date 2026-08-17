package com.schoolbus.gateway.routing;

import com.schoolbus.gateway.config.TransportQueryRouteResilienceProperties;
import com.schoolbus.gateway.observability.TransportQueryRetryMetricsGatewayFilter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.factory.RetryGatewayFilterFactory;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.support.RouteMetadataUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

@Configuration(proxyBeanMethods = false)
public class CoreServiceRoutes {

    public static final String TRIPS_ROUTE_ID = "school-bus-transport-query-trips";
    public static final String SEATS_ROUTE_ID = "school-bus-transport-query-seats";
    public static final String IAM_ACCOUNTS_ROUTE_ID = "school-bus-iam-accounts";
    public static final String IAM_AUTH_ROUTE_ID = "school-bus-iam-auth";
    public static final String CORE_ROUTE_ID = "school-bus-core-api";

    @Bean
    RouteLocator coreApiRoutes(
            RouteLocatorBuilder builder,
            TransportQueryRouteResilienceProperties resilience,
            MeterRegistry meterRegistry,
            @Value("${school-bus.gateway.core-service-id:school-bus-core}")
            String coreServiceId,
            @Value("${school-bus.gateway.transport-query-service-id:school-bus-transport-query}")
            String transportQueryServiceId,
            @Value("${school-bus.gateway.iam-service-id:school-bus-iam}")
            String iamServiceId
    ) {
        int connectTimeoutMs = Math.toIntExact(resilience.connectTimeout().toMillis());
        long responseTimeoutMs = resilience.responseTimeout().toMillis();

        return builder.routes()
                .route(TRIPS_ROUTE_ID, route -> route
                        .order(-20)
                        .method(HttpMethod.GET)
                        .and()
                        .path("/api/v1/trips")
                        .filters(filters -> applyTransportQueryFilters(
                                filters,
                                resilience,
                                meterRegistry,
                                TRIPS_ROUTE_ID
                        ))
                        .metadata(RouteMetadataUtils.CONNECT_TIMEOUT_ATTR, connectTimeoutMs)
                        .metadata(RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR, responseTimeoutMs)
                        .uri("lb://" + transportQueryServiceId))
                .route(SEATS_ROUTE_ID, route -> route
                        .order(-19)
                        .method(HttpMethod.GET)
                        .and()
                        .path("/api/v1/trips/*/seats")
                        .filters(filters -> applyTransportQueryFilters(
                                filters,
                                resilience,
                                meterRegistry,
                                SEATS_ROUTE_ID
                        ))
                        .metadata(RouteMetadataUtils.CONNECT_TIMEOUT_ATTR, connectTimeoutMs)
                        .metadata(RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR, responseTimeoutMs)
                        .uri("lb://" + transportQueryServiceId))
                .route(IAM_ACCOUNTS_ROUTE_ID, route -> route
                        .order(-15)
                        .method(HttpMethod.POST)
                        .and()
                        .path("/api/v1/accounts")
                        .filters(filters -> stripUntrustedIdentityHeaders(filters))
                        .uri("lb://" + iamServiceId))
                .route(IAM_AUTH_ROUTE_ID, route -> route
                        .order(-14)
                        .path("/api/v1/auth/**")
                        .filters(filters -> stripUntrustedIdentityHeaders(filters))
                        .uri("lb://" + iamServiceId))
                .route(CORE_ROUTE_ID, route -> route
                        .order(0)
                        .path("/api/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .filters(filters -> stripUntrustedIdentityHeaders(filters))
                        .uri("lb://" + coreServiceId))
                .build();
    }

    private static GatewayFilterSpec applyTransportQueryFilters(
            GatewayFilterSpec filters,
            TransportQueryRouteResilienceProperties resilience,
            MeterRegistry meterRegistry,
            String routeId
    ) {
        stripUntrustedIdentityHeaders(filters);
        filters.filter(new TransportQueryRetryMetricsGatewayFilter(meterRegistry, routeId));
        if (resilience.retryFilterEnabled()) {
            filters.retry(retry -> configureRetry(retry, resilience));
        }
        return filters;
    }

    private static GatewayFilterSpec stripUntrustedIdentityHeaders(GatewayFilterSpec filters) {
        return filters
                .removeRequestHeader("X-User-Id")
                .removeRequestHeader("X-User-Roles")
                .removeRequestHeader("X-Authenticated-User");
    }

    private static void configureRetry(
            RetryGatewayFilterFactory.RetryConfig retry,
            TransportQueryRouteResilienceProperties resilience
    ) {
        // Clear default SERVER_ERROR series so 500 is never retried.
        retry.setSeries();
        retry.setRetries(resilience.retries());
        retry.setStatuses(
                HttpStatus.BAD_GATEWAY,
                HttpStatus.SERVICE_UNAVAILABLE,
                HttpStatus.GATEWAY_TIMEOUT
        );
        retry.setMethods(HttpMethod.GET);
        // Keep default IOException/TimeoutException retries for dead-instance races.
        retry.setBackoff(
                resilience.firstBackoff(),
                resilience.maxBackoff(),
                2,
                false
        );
    }
}
