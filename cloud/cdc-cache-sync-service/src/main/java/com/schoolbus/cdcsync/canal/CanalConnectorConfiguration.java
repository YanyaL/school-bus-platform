package com.schoolbus.cdcsync.canal;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import com.schoolbus.cdcsync.config.CanalConnectionProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetSocketAddress;

@Configuration(proxyBeanMethods = false)
public class CanalConnectorConfiguration {

    @Bean
    @ConditionalOnProperty(
            prefix = "school-bus.cdc.canal",
            name = "enabled",
            havingValue = "true"
    )
    CanalConnector canalConnector(CanalConnectionProperties properties) {
        return CanalConnectors.newSingleConnector(
                new InetSocketAddress(properties.host(), properties.port()),
                properties.destination(),
                properties.username(),
                properties.password()
        );
    }
}
