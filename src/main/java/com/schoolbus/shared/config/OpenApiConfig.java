package com.schoolbus.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI schoolBusOpenApi() {
        return new OpenAPI().info(new Info()
                .title("School Bus Platform API")
                .description("Campus school bus booking platform")
                .version("v1"));
    }
}
