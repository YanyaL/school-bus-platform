package com.schoolbus.bookingservice.application.trippublication;

public record InventoryReadinessResult(
        int scanned,
        int ready,
        int waiting,
        int failed
) {
}
