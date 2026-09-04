package com.schoolbus.bookingservice.config;

import com.schoolbus.bookingservice.application.booking.InventoryReadinessGate;
import com.schoolbus.bookingservice.domain.trip.TripReference;
import com.schoolbus.bookingservice.infrastructure.persistence.trippublication.InventoryReadinessMapper;
import com.schoolbus.bookingservice.infrastructure.persistence.trippublication.MyBatisInventoryReadinessGate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class InventoryReadinessGateConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(InventoryReadinessGateConfiguration.class)
            .withBean(
                    InventoryReadinessMapper.class,
                    () -> mock(InventoryReadinessMapper.class)
            );

    @Test
    void remainsPermissiveByDefaultForSafeDeployment() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(InventoryReadinessGate.class);
            InventoryReadinessGate gate = context.getBean(
                    InventoryReadinessGate.class
            );
            assertThat(gate.isReady(TripReference.of(1L), 1L)).isTrue();
            assertThat(gate).isNotInstanceOf(
                    MyBatisInventoryReadinessGate.class
            );
        });
    }

    @Test
    void usesTheDatabaseGateOnlyWhenExplicitlyEnabled() {
        runner.withPropertyValues(
                        "school-bus.booking.inventory-readiness-gate.enabled=true"
                )
                .run(context -> assertThat(context.getBean(
                        InventoryReadinessGate.class
                )).isInstanceOf(MyBatisInventoryReadinessGate.class));
    }
}
