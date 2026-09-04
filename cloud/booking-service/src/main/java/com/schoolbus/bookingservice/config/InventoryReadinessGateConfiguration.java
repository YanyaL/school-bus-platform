package com.schoolbus.bookingservice.config;

import com.schoolbus.bookingservice.application.booking.InventoryReadinessGate;
import com.schoolbus.bookingservice.infrastructure.persistence.trippublication.InventoryReadinessMapper;
import com.schoolbus.bookingservice.infrastructure.persistence.trippublication.MyBatisInventoryReadinessGate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Objects;

@Configuration(proxyBeanMethods = false)
public class InventoryReadinessGateConfiguration {

    @Bean
    @ConditionalOnProperty(
            prefix = "school-bus.booking.inventory-readiness-gate",
            name = "enabled",
            havingValue = "true"
    )
    InventoryReadinessGate enforcingInventoryReadinessGate(
            InventoryReadinessMapper mapper
    ) {
        return new MyBatisInventoryReadinessGate(mapper);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "school-bus.booking.inventory-readiness-gate",
            name = "enabled",
            havingValue = "false",
            matchIfMissing = true
    )
    InventoryReadinessGate permissiveInventoryReadinessGate() {
        return (tripReference, tripVersion) -> {
            Objects.requireNonNull(
                    tripReference,
                    "tripReference must not be null"
            );
            if (tripVersion <= 0L) {
                throw new IllegalArgumentException(
                        "tripVersion must be positive"
                );
            }
            return true;
        };
    }
}
