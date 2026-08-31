package com.schoolbus.transport.application.trip;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TripPublishedEventTest {
    private static final Instant NOW = Instant.parse("2026-08-31T00:00:00Z");

    @Test
    void capturesImmutableSeatSnapshot() {
        List<String> seats = new ArrayList<>(List.of("1", "2"));
        TripPublishedEvent event = event(seats);
        seats.add("3");
        assertThat(event.seatNumbers()).containsExactly("1", "2");
        assertThat(event.totalSeats()).isEqualTo(2);
        assertThat(event.price().toPlainString()).isEqualTo("5.00");
        assertThatThrownBy(() -> event.seatNumbers().add("4")).isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @MethodSource("invalidSeats")
    void rejectsInvalidSeatSnapshots(List<String> seats) {
        assertThatThrownBy(() -> event(seats)).isInstanceOf(RuntimeException.class);
    }

    static Stream<List<String>> invalidSeats() {
        return Stream.of(List.of(), List.of("1", "1"), List.of(" "), Arrays.asList("1", null));
    }

    @Test
    void rejectsDeadlineThatAlreadyArrived() {
        assertThatThrownBy(() -> new TripPublishedEvent(1, UUID.randomUUID(), 1, List.of("1"),
                BigDecimal.ONE, NOW, NOW.plusSeconds(120), NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidIdentityOrVersion() {
        assertThatThrownBy(() -> new TripPublishedEvent(0, UUID.randomUUID(), 1, List.of("1"),
                BigDecimal.ONE, NOW.plusSeconds(60), NOW.plusSeconds(120), NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TripPublishedEvent(1, UUID.randomUUID(), 0, List.of("1"),
                BigDecimal.ONE, NOW.plusSeconds(60), NOW.plusSeconds(120), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private TripPublishedEvent event(List<String> seats) {
        return new TripPublishedEvent(1, UUID.randomUUID(), 1, seats, new BigDecimal("5"),
                NOW.plusSeconds(60), NOW.plusSeconds(120), NOW);
    }
}
