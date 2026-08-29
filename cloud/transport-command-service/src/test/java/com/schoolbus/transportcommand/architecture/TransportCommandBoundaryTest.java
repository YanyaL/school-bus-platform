package com.schoolbus.transportcommand.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TransportCommandBoundaryTest {

    private static final List<String> FORBIDDEN_TABLES = List.of(
            "booking_order",
            "booking_seat_inventory",
            "payment_record",
            "transport_trip"
    );

    @Test
    void phaseOneDoesNotReachIntoBookingPaymentOrTripTables() throws IOException {
        Path sourceRoot = Path.of("src", "main");
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            List<Path> violations = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java")
                            || path.toString().endsWith(".xml"))
                    .filter(this::containsForbiddenTable)
                    .toList();

            assertThat(violations)
                    .as("Transport Command phase 1 must own only vehicle and route persistence")
                    .isEmpty();
        }
    }

    private boolean containsForbiddenTable(Path path) {
        try {
            String content = Files.readString(path).toLowerCase();
            return FORBIDDEN_TABLES.stream().anyMatch(content::contains);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to inspect " + path, exception);
        }
    }
}
