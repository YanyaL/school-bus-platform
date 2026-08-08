package com.schoolbus.booking.infrastructure.persistence.seat;

import com.schoolbus.booking.application.booking.SeatLockRequest;
import com.schoolbus.booking.domain.order.BookingNumber;
import com.schoolbus.booking.domain.order.SeatNumber;
import com.schoolbus.booking.domain.trip.TripReference;
import com.schoolbus.shared.domain.identity.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyBatisTripSeatReservationTest {

    private static final Instant LOCKED_AT =
            Instant.parse("2026-08-08T00:00:00Z");
    private static final Instant EXPIRES_AT =
            Instant.parse("2026-08-08T00:15:00Z");

    @Mock
    private TripSeatMapper mapper;

    private MyBatisTripSeatReservation reservation;

    @BeforeEach
    void setUp() {
        reservation = new MyBatisTripSeatReservation(mapper);
    }

    @Test
    void shouldReturnTrueWhenAvailableSeatIsLocked() {
        SeatLockRequest request = request();
        when(mapper.tryLockSeat(
                2001L,
                "A01",
                "55555555-5555-5555-5555-555555555555",
                1001L,
                toDatabaseTime(EXPIRES_AT),
                toDatabaseTime(LOCKED_AT)
        )).thenReturn(1);

        boolean locked = reservation.tryLockSeat(request);

        assertThat(locked).isTrue();
        verify(mapper).tryLockSeat(
                2001L,
                "A01",
                "55555555-5555-5555-5555-555555555555",
                1001L,
                toDatabaseTime(EXPIRES_AT),
                toDatabaseTime(LOCKED_AT)
        );
    }

    @Test
    void shouldReturnFalseWhenSeatCannotBeLocked() {
        SeatLockRequest request = request();
        when(mapper.tryLockSeat(
                2001L,
                "A01",
                "55555555-5555-5555-5555-555555555555",
                1001L,
                toDatabaseTime(EXPIRES_AT),
                toDatabaseTime(LOCKED_AT)
        )).thenReturn(0);

        assertThat(reservation.tryLockSeat(request)).isFalse();
    }

    @Test
    void shouldRejectUnexpectedAffectedRowCount() {
        SeatLockRequest request = request();
        when(mapper.tryLockSeat(
                2001L,
                "A01",
                "55555555-5555-5555-5555-555555555555",
                1001L,
                toDatabaseTime(EXPIRES_AT),
                toDatabaseTime(LOCKED_AT)
        )).thenReturn(2);

        assertThatThrownBy(() -> reservation.tryLockSeat(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "seat lock update affected an unexpected number of rows: 2"
                );
    }

    private SeatLockRequest request() {
        return new SeatLockRequest(
                TripReference.of(2001L),
                SeatNumber.of("A01"),
                BookingNumber.of(
                        "55555555-5555-5555-5555-555555555555"
                ),
                UserId.of(1001L),
                EXPIRES_AT,
                LOCKED_AT
        );
    }

    private LocalDateTime toDatabaseTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
