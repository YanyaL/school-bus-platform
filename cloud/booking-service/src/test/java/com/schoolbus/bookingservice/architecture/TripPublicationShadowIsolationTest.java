package com.schoolbus.bookingservice.architecture;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class TripPublicationShadowIsolationTest {
    @Test
    void observationCodeMustNotDependOnLiveBusinessStateOrSharedConsumptionCache() throws Exception {
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            for (Path path : paths.filter(Files::isRegularFile).filter(p -> p.toString().contains("trippublication")).toList()) {
                assertThat(Files.readString(path)).as(path.toString()).doesNotContain(
                        "booking_order", "booking_trip_inventory", "transport_trip_seat", "event_consumed",
                        "BookingOrderRepository", "SeatInventoryRepository", "TripSeatReservationPort", "ConsumedEventStore",
                        "RedisTemplate", "application.booking", "support.payment");
            }
        }
    }
}
