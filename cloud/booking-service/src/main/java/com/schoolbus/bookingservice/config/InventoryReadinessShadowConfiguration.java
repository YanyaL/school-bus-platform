package com.schoolbus.bookingservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolbus.bookingservice.application.trippublication.InventoryReadinessApplicationService;
import com.schoolbus.bookingservice.application.trippublication.InventoryReadinessStore;
import com.schoolbus.bookingservice.application.trippublication.InventoryReadinessTransaction;
import com.schoolbus.bookingservice.infrastructure.persistence.trippublication.InventoryReadinessMapper;
import com.schoolbus.bookingservice.infrastructure.persistence.trippublication.MyBatisInventoryReadinessStore;
import com.schoolbus.bookingservice.infrastructure.scheduling.InventoryReadinessScheduler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "school-bus.booking.inventory-readiness-shadow",
        name = "enabled",
        havingValue = "true"
)
public class InventoryReadinessShadowConfiguration {
    @Bean
    InventoryReadinessStore inventoryReadinessStore(
            InventoryReadinessMapper mapper
    ) {
        return new MyBatisInventoryReadinessStore(mapper);
    }

    @Bean
    InventoryReadinessTransaction inventoryReadinessTransaction(
            InventoryReadinessStore store,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        return new InventoryReadinessTransaction(store, objectMapper, clock);
    }

    @Bean
    InventoryReadinessApplicationService inventoryReadinessApplicationService(
            InventoryReadinessStore store,
            InventoryReadinessTransaction transaction,
            @Value("${school-bus.booking.inventory-readiness-shadow.batch-size:100}")
            int batchSize
    ) {
        return new InventoryReadinessApplicationService(
                store,
                transaction,
                batchSize
        );
    }

    @Bean
    InventoryReadinessScheduler inventoryReadinessScheduler(
            InventoryReadinessApplicationService service
    ) {
        return new InventoryReadinessScheduler(service);
    }
}
