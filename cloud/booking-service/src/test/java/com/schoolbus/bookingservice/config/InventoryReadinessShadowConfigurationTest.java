package com.schoolbus.bookingservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolbus.bookingservice.application.trippublication.InventoryReadinessApplicationService;
import com.schoolbus.bookingservice.application.trippublication.InventoryReadinessStore;
import com.schoolbus.bookingservice.application.trippublication.InventoryReadinessTransaction;
import com.schoolbus.bookingservice.infrastructure.persistence.trippublication.InventoryReadinessMapper;
import com.schoolbus.bookingservice.infrastructure.scheduling.InventoryReadinessScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class InventoryReadinessShadowConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(InventoryReadinessShadowConfiguration.class)
            .withBean(InventoryReadinessMapper.class, () -> mock(
                    InventoryReadinessMapper.class
            ))
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(Clock.class, Clock::systemUTC);

    @Test
    void remainsDisabledByDefault() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(InventoryReadinessStore.class);
            assertThat(context).doesNotHaveBean(InventoryReadinessScheduler.class);
        });
    }

    @Test
    void createsTheCompleteVerifierOnlyWhenExplicitlyEnabled() {
        runner.withPropertyValues(
                        "school-bus.booking.inventory-readiness-shadow.enabled=true",
                        "school-bus.booking.inventory-readiness-shadow.batch-size=25"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(InventoryReadinessStore.class);
                    assertThat(context).hasSingleBean(InventoryReadinessTransaction.class);
                    assertThat(context).hasSingleBean(
                            InventoryReadinessApplicationService.class
                    );
                    assertThat(context).hasSingleBean(
                            InventoryReadinessScheduler.class
                    );
                });
    }
}
