package com.schoolbus.gateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TransportQueryRouteResilienceProperties.class)
public class GatewayConfiguration {
}
