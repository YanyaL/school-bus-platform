package com.schoolbus.transport.infrastructure.persistence.seat;

import com.schoolbus.transport.domain.trip.TripId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyBatisTripSeatRepositoryTest {

    private static final Instant NOW =
            Instant.parse("2026-08-14T00:00:00Z");

    @Mock
    private TripSeatQueryMapper mapper;

    private MyBatisTripSeatRepository repository;

    @BeforeEach
    void setUp() {
        repository = new MyBatisTripSeatRepository(mapper);
    }

    @Test
    void shouldBatchInitializeAvailableSeats() {
        List<String> seats = List.of("1", "2", "3");
        LocalDateTime databaseTime = LocalDateTime.ofInstant(
                NOW,
                ZoneOffset.UTC
        );
        when(mapper.insertAvailableSeats(5001L, seats, databaseTime))
                .thenReturn(3);

        repository.initializeSeats(TripId.of(5001L), seats, NOW);

        verify(mapper).insertAvailableSeats(
                5001L,
                seats,
                databaseTime
        );
    }

    @Test
    void shouldRejectPartialSeatInitialization() {
        List<String> seats = List.of("1", "2", "3");
        when(mapper.insertAvailableSeats(
                5001L,
                seats,
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)
        )).thenReturn(2);

        assertThatThrownBy(() -> repository.initializeSeats(
                TripId.of(5001L),
                seats,
                NOW
        )).isInstanceOf(IllegalStateException.class);
    }
}
