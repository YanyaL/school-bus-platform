package com.schoolbus.gateway.routing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

@Configuration(proxyBeanMethods = false)
public class CoreServiceRoutes {

    @Bean
    RouteLocator coreApiRoutes(
            RouteLocatorBuilder builder,
            @Value("${school-bus.gateway.core-service-id:school-bus-core}")
            String coreServiceId,
            @Value("${school-bus.gateway.transport-query-service-id:school-bus-transport-query}")
            String transportQueryServiceId
    ) {
        return builder.routes()
                .route("school-bus-transport-query-trips", route -> route
                        .order(-20)
                        .method(HttpMethod.GET)
                        .and()
                        .path("/api/v1/trips")
                        .filters(filters -> filters
                                .removeRequestHeader("X-User-Id")
                                .removeRequestHeader("X-User-Roles")
                                .removeRequestHeader("X-Authenticated-User"))
                        .uri("lb://" + transportQueryServiceId))
                .route("school-bus-transport-query-seats", route -> route
                        .order(-19)
                        .method(HttpMethod.GET)
                        .and()
                        .path("/api/v1/trips/*/seats")
                        .filters(filters -> filters
                                .removeRequestHeader("X-User-Id")
                                .removeRequestHeader("X-User-Roles")
                                .removeRequestHeader("X-Authenticated-User"))
                        .uri("lb://" + transportQueryServiceId))
                .route("school-bus-core-api", route -> route
                        .order(0)
                        .path("/api/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .filters(filters -> filters
                                .removeRequestHeader("X-User-Id")
                                .removeRequestHeader("X-User-Roles")
                                .removeRequestHeader("X-Authenticated-User"))
                        .uri("lb://" + coreServiceId))
                .build();
    }
}
