package com.schoolbus.bookingservice.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryReadinessBoundaryTest {
    @Test
    void readinessVerifierOnlyReadsLiveInventoryTables() throws Exception {
        Path mapper = Path.of(
                "src/main/java/com/schoolbus/bookingservice/infrastructure/"
                        + "persistence/trippublication/InventoryReadinessMapper.java"
        );
        String source = Files.readString(mapper).toUpperCase();

        assertThat(source).contains(
                "FROM BOOKING_TRIP_INVENTORY",
                "FROM TRANSPORT_TRIP_SEAT"
        );
        assertThat(source).doesNotContain(
                "UPDATE BOOKING_TRIP_INVENTORY",
                "INSERT INTO BOOKING_TRIP_INVENTORY (",
                "DELETE FROM BOOKING_TRIP_INVENTORY",
                "UPDATE TRANSPORT_TRIP_SEAT",
                "INSERT INTO TRANSPORT_TRIP_SEAT",
                "DELETE FROM TRANSPORT_TRIP_SEAT"
        );
    }
}
