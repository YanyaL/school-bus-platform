package com.schoolbus.transport.application.trip;

import com.schoolbus.transport.domain.trip.BusTrip;
import com.schoolbus.transport.domain.trip.BusTripRepository;
import com.schoolbus.transport.domain.trip.Money;
import com.schoolbus.transport.domain.trip.RouteId;
import com.schoolbus.transport.domain.trip.TripId;
import com.schoolbus.transport.domain.trip.TripNumber;
import com.schoolbus.transport.domain.trip.TripStatus;
import com.schoolbus.transport.domain.trip.VehicleId;
import com.schoolbus.transport.infrastructure.persistence.seat.TripSeatQueryMapper;
import com.schoolbus.transport.infrastructure.persistence.seat.TripSeatStatusDataObject;
import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripSeatQueryApplicationServiceTest {

    private static final Instant BOOKING_DEADLINE =
            Instant.parse("2026-08-05T08:30:00Z");

    @Mock
    private BusTripRepository busTripRepository;

    @Mock
    private TripSeatQueryMapper tripSeatQueryMapper;

    @InjectMocks
    private TripSeatQueryApplicationService service;

    @Test
    void shouldReturnSeatMapWithoutSensitiveFields() {
        when(busTripRepository.findById(TripId.of(1001L)))
                .thenReturn(Optional.of(openTrip()));
        TripSeatStatusDataObject available = new TripSeatStatusDataObject();
        available.setSeatNumber("A01");
        available.setStatus("AVAILABLE");
        TripSeatStatusDataObject locked = new TripSeatStatusDataObject();
        locked.setSeatNumber("A02");
        locked.setStatus("LOCKED");
        when(tripSeatQueryMapper.selectSeatStatusesByTripId(1001L))
                .thenReturn(List.of(available, locked));

        TripSeatMapView view = service.findTripSeatMap(1001L);

        assertThat(view.tripId()).isEqualTo(1001L);
        assertThat(view.bookingDeadline()).isEqualTo(BOOKING_DEADLINE);
        assertThat(view.seats()).containsExactly(
                new TripSeatView("A01", "AVAILABLE"),
                new TripSeatView("A02", "LOCKED")
        );
    }

    @Test
    void shouldRejectUnknownTrip() {
        when(busTripRepository.findById(TripId.of(9999L)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findTripSeatMap(9999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).errorCode()
                ).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private BusTrip openTrip() {
        return BusTrip.restore(
                TripId.of(1001L),
                TripNumber.of(
                        "11111111-1111-1111-1111-111111111111"
                ),
                VehicleId.of(3001L),
                RouteId.of(2001L),
                Instant.parse("2026-08-05T09:00:00Z"),
                BOOKING_DEADLINE,
                new Money(new BigDecimal("5.00")),
                TripStatus.OPEN_FOR_BOOKING,
                1L,
                Instant.parse("2026-08-04T00:00:00Z"),
                Instant.parse("2026-08-04T00:00:00Z")
        );
    }
}
