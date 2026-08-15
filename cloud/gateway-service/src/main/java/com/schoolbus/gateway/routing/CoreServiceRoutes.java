package com.schoolbus.gateway.routing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CoreServiceRoutes {

    @Bean
    RouteLocator coreApiRoutes(
            RouteLocatorBuilder builder,
            @Value("${school-bus.gateway.core-service-id:school-bus-core}") String coreServiceId
    ) {
        return builder.routes()
                .route("school-bus-core-api", route -> route
                        .path("/api/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .filters(filters -> filters
                                .removeRequestHeader("X-User-Id")
                                .removeRequestHeader("X-User-Roles")
                                .removeRequestHeader("X-Authenticated-User"))
                        .uri("lb://" + coreServiceId))
                .build();
    }
}
