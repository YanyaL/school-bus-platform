package com.schoolbus.transportquery.application;

import com.schoolbus.transportquery.api.BusinessException;
import com.schoolbus.transportquery.api.ErrorCode;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripSeatQueryServiceTest {

    private static final String TRIP_NUMBER =
            "11111111-1111-1111-1111-111111111111";

    @Mock
    private TripQueryRepository tripQueryRepository;

    @InjectMocks
    private TripSeatQueryService service;

    @Test
    void shouldRejectInvalidUuid() {
        assertThatThrownBy(() -> service.findTripSeatMap("bad"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void shouldReturnNotFoundWhenTripMissing() {
        when(tripQueryRepository.findByTripNumber(TRIP_NUMBER))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findTripSeatMap(TRIP_NUMBER))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void shouldLoadSeatsByInternalTripIdOrderedByMapper() {
        when(tripQueryRepository.findByTripNumber(TRIP_NUMBER))
                .thenReturn(Optional.of(new TripRecord(
                        1001L,
                        TRIP_NUMBER,
                        Instant.parse("2026-08-05T08:00:00Z")
                )));
        when(tripQueryRepository.findSeatStatusesByTripId(1001L))
                .thenReturn(List.of(
                        new TripSeatStatusView("A01", "AVAILABLE"),
                        new TripSeatStatusView("A02", "SOLD")
                ));

        TripSeatMapView view = service.findTripSeatMap(TRIP_NUMBER);

        assertThat(view.tripNumber()).isEqualTo(TRIP_NUMBER);
        assertThat(view.seats()).extracting(TripSeatStatusView::seatNumber)
                .containsExactly("A01", "A02");
        verify(tripQueryRepository).findSeatStatusesByTripId(1001L);
    }
}
