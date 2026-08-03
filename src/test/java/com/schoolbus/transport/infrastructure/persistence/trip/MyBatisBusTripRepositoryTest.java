package com.schoolbus.transport.infrastructure.persistence.trip;

import com.schoolbus.transport.domain.trip.BusTrip;
import com.schoolbus.transport.domain.trip.Money;
import com.schoolbus.transport.domain.trip.RouteId;
import com.schoolbus.transport.domain.trip.TripId;
import com.schoolbus.transport.domain.trip.TripNumber;
import com.schoolbus.transport.domain.trip.TripStatus;
import com.schoolbus.transport.domain.trip.VehicleId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyBatisBusTripRepositoryTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-08-03T00:00:00Z");
    private static final Instant BOOKING_DEADLINE =
            Instant.parse("2026-08-04T07:30:00Z");
    private static final Instant DEPARTURE_TIME =
            Instant.parse("2026-08-04T08:00:00Z");

    @Mock
    private TripMapper tripMapper;

    private MyBatisBusTripRepository repository;

    @BeforeEach
    void setUp() {
        repository = new MyBatisBusTripRepository(tripMapper);
    }

    @Test
    void shouldInsertNewDraftTrip() {
        BusTrip trip = draftTrip();
        when(tripMapper.insertTrip(any())).thenReturn(1);

        BusTrip saved = repository.save(trip);

        assertThat(saved).isSameAs(trip);
        ArgumentCaptor<TripDataObject> captor =
                ArgumentCaptor.forClass(TripDataObject.class);
        verify(tripMapper).insertTrip(captor.capture());
        TripDataObject inserted = captor.getValue();
        assertThat(inserted.getId()).isEqualTo(1001L);
        assertThat(inserted.getTripNumber())
                .isEqualTo(
                        "11111111-1111-1111-1111-111111111111"
                );
        assertThat(inserted.getStatus()).isEqualTo("DRAFT");
        assertThat(inserted.getVersion()).isZero();
    }

    @Test
    void shouldUpdateUsingPreviousVersion() {
        BusTrip trip = draftTrip();
        trip.openForBooking(CREATED_AT.plusSeconds(60));
        when(tripMapper.updateWithVersion(any(), any()))
                .thenReturn(1);

        repository.save(trip);

        ArgumentCaptor<TripDataObject> tripCaptor =
                ArgumentCaptor.forClass(TripDataObject.class);
        ArgumentCaptor<Long> versionCaptor =
                ArgumentCaptor.forClass(Long.class);
        verify(tripMapper).updateWithVersion(
                tripCaptor.capture(),
                versionCaptor.capture()
        );
        assertThat(tripCaptor.getValue().getStatus())
                .isEqualTo("OPEN_FOR_BOOKING");
        assertThat(tripCaptor.getValue().getVersion())
                .isEqualTo(1L);
        assertThat(versionCaptor.getValue()).isZero();
    }

    @Test
    void shouldReportOptimisticLockConflict() {
        BusTrip trip = draftTrip();
        trip.openForBooking(CREATED_AT.plusSeconds(60));
        when(tripMapper.updateWithVersion(any(), any()))
                .thenReturn(0);

        assertThatThrownBy(() -> repository.save(trip))
                .isInstanceOf(
                        OptimisticLockingFailureException.class
                )
                .hasMessage(
                        "bus trip was modified by another request"
                );
    }

    @Test
    void shouldRestoreTripFromDataObject() {
        TripDataObject dataObject = dataObject();
        when(tripMapper.selectById(1001L))
                .thenReturn(dataObject);

        Optional<BusTrip> result = repository.findById(
                TripId.of(1001L)
        );

        assertThat(result).isPresent();
        BusTrip trip = result.orElseThrow();
        assertThat(trip.tripNumber().toString())
                .isEqualTo(dataObject.getTripNumber());
        assertThat(trip.status()).isEqualTo(TripStatus.DRAFT);
        assertThat(trip.price()).isEqualTo(Money.of("5.00"));
        assertThat(trip.version()).isZero();
    }

    private BusTrip draftTrip() {
        return BusTrip.draft(
                TripId.of(1001L),
                TripNumber.of(
                        "11111111-1111-1111-1111-111111111111"
                ),
                VehicleId.of(3001L),
                RouteId.of(2001L),
                DEPARTURE_TIME,
                BOOKING_DEADLINE,
                Money.of("5.00"),
                CREATED_AT
        );
    }

    private TripDataObject dataObject() {
        TripDataObject dataObject = new TripDataObject();
        dataObject.setId(1001L);
        dataObject.setTripNumber(
                "11111111-1111-1111-1111-111111111111"
        );
        dataObject.setVehicleId(3001L);
        dataObject.setRouteId(2001L);
        dataObject.setDepartureTime(
                LocalDateTime.ofInstant(
                        DEPARTURE_TIME,
                        ZoneOffset.UTC
                )
        );
        dataObject.setBookingDeadline(
                LocalDateTime.ofInstant(
                        BOOKING_DEADLINE,
                        ZoneOffset.UTC
                )
        );
        dataObject.setPrice(new BigDecimal("5.00"));
        dataObject.setStatus("DRAFT");
        dataObject.setVersion(0L);
        dataObject.setCreatedAt(
                LocalDateTime.ofInstant(
                        CREATED_AT,
                        ZoneOffset.UTC
                )
        );
        dataObject.setUpdatedAt(
                LocalDateTime.ofInstant(
                        CREATED_AT,
                        ZoneOffset.UTC
                )
        );
        return dataObject;
    }
}
