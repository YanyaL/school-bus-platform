package com.schoolbus.bookingservice.infrastructure.scheduling;

import com.schoolbus.bookingservice.application.trippublication.InventoryReadinessApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Objects;

public class InventoryReadinessScheduler {
    private static final Logger log = LoggerFactory.getLogger(
            InventoryReadinessScheduler.class
    );
    private final InventoryReadinessApplicationService service;

    public InventoryReadinessScheduler(
            InventoryReadinessApplicationService service
    ) {
        this.service = Objects.requireNonNull(service);
    }

    @Scheduled(
            initialDelayString = "${school-bus.booking.inventory-readiness-shadow.initial-delay-ms:15000}",
            fixedDelayString = "${school-bus.booking.inventory-readiness-shadow.fixed-delay-ms:10000}"
    )
    public void verifyPending() {
        var result = service.verifyPending();
        log.info(
                "Inventory readiness shadow: scanned={}, ready={}, waiting={}, failed={}",
                result.scanned(), result.ready(), result.waiting(), result.failed()
        );
    }
}
