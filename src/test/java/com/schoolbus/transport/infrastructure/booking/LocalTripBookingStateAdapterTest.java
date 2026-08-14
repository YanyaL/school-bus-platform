package com.schoolbus.transport.infrastructure.booking;

import com.schoolbus.booking.domain.order.BookingOrderRepository;
import com.schoolbus.booking.domain.trip.TripReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocalTripBookingStateAdapterTest {

    @Test
    void shouldQueryActiveBookingStateThroughBookingPort() {
        BookingOrderRepository repository = mock(
                BookingOrderRepository.class
        );
        when(repository.existsActiveByTripReference(
                TripReference.of(5001L)
        )).thenReturn(true);
        LocalTripBookingStateAdapter adapter =
                new LocalTripBookingStateAdapter(repository);

        assertThat(adapter.hasActiveBookings(5001L)).isTrue();
        verify(repository).existsActiveByTripReference(
                TripReference.of(5001L)
        );
    }
}
