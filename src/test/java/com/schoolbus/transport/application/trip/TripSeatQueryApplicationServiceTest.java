package com.schoolbus.transport.application.trip;

import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.transport.domain.trip.BusTrip;
import com.schoolbus.transport.domain.trip.BusTripRepository;
import com.schoolbus.transport.domain.trip.Money;
import com.schoolbus.transport.domain.trip.RouteId;
import com.schoolbus.transport.domain.trip.TripId;
import com.schoolbus.transport.domain.trip.TripNumber;
import com.schoolbus.transport.domain.trip.TripSeatRepository;
import com.schoolbus.transport.domain.trip.TripSeatStatus;
import com.schoolbus.transport.domain.trip.TripStatus;
import com.schoolbus.transport.domain.trip.VehicleId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripSeatQueryApplicationServiceTest {

    @Mock
    private BusTripRepository busTripRepository;

    @Mock
    private TripSeatRepository tripSeatRepository;

    @InjectMocks
    private TripSeatQueryApplicationService service;

    @Test
    void shouldReturnSeatMapWithoutSensitiveFields() {
        TripId tripId = TripId.of(1001L);
        when(busTripRepository.findById(tripId))
                .thenReturn(Optional.of(sampleTrip(tripId)));
        when(tripSeatRepository.findSeatStatusesByTripId(tripId))
                .thenReturn(List.of(
                        new TripSeatStatus("A01", "AVAILABLE"),
                        new TripSeatStatus("A02", "LOCKED")
                ));

        TripSeatMapView view = service.findTripSeatMap(1001L);

        assertThat(view.tripId()).isEqualTo(1001L);
        assertThat(view.seats()).hasSize(2);
        assertThat(view.seats().getFirst().status())
                .isEqualTo("AVAILABLE");
    }

    @Test
    void shouldRejectUnknownTrip() {
        when(busTripRepository.findById(TripId.of(9999L)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findTripSeatMap(9999L))
                .isInstanceOf(BusinessException.class);
    }

    private BusTrip sampleTrip(TripId tripId) {
        return BusTrip.restore(
                tripId,
                TripNumber.of(
                        "11111111-1111-1111-1111-111111111111"
                ),
                VehicleId.of(3001L),
                RouteId.of(2001L),
                Instant.parse("2026-08-05T09:00:00Z"),
                Instant.parse("2026-08-05T08:30:00Z"),
                Money.of("5.00"),
                TripStatus.OPEN_FOR_BOOKING,
                1L,
                Instant.parse("2026-08-05T07:00:00Z"),
                Instant.parse("2026-08-05T07:00:00Z")
        );
    }
}
