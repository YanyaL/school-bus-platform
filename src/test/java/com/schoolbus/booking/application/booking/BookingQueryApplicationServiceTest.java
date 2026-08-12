package com.schoolbus.booking.application.booking;

import com.schoolbus.booking.domain.order.BookingAmount;
import com.schoolbus.booking.domain.order.BookingId;
import com.schoolbus.booking.domain.order.BookingNumber;
import com.schoolbus.booking.domain.order.BookingOrder;
import com.schoolbus.booking.domain.order.BookingOrderRepository;
import com.schoolbus.booking.domain.order.BookingRequestNumber;
import com.schoolbus.booking.domain.order.BookingStatus;
import com.schoolbus.booking.domain.order.SeatNumber;
import com.schoolbus.booking.domain.trip.TripReference;
import com.schoolbus.shared.domain.identity.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingQueryApplicationServiceTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-08-08T00:00:00Z");
    private static final Instant EXPIRES_AT =
            Instant.parse("2026-08-08T00:15:00Z");

    @Mock
    private BookingOrderRepository bookingOrderRepository;

    @InjectMocks
    private BookingQueryApplicationService service;

    @Test
    void shouldListBookingsForCurrentUser() {
        ListMyBookingsQuery query = new ListMyBookingsQuery(
                1001L,
                null,
                0,
                20,
                false
        );
        BookingOrder order = pendingOrder();
        when(bookingOrderRepository.findByUserId(
                UserId.of(1001L),
                null,
                0,
                20,
                false
        )).thenReturn(List.of(order));

        List<BookingSummaryView> result = service.listMyBookings(query);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().bookingNumber())
                .isEqualTo("55555555-5555-5555-5555-555555555555");
        assertThat(result.getFirst().status())
                .isEqualTo(BookingStatus.PENDING_PAYMENT);
    }

    @Test
    void shouldCountBookingsForCurrentUser() {
        ListMyBookingsQuery query = new ListMyBookingsQuery(
                1001L,
                BookingStatus.PENDING_PAYMENT,
                1,
                10,
                true
        );
        when(bookingOrderRepository.countByUserId(
                UserId.of(1001L),
                BookingStatus.PENDING_PAYMENT
        )).thenReturn(3L);

        long total = service.countMyBookings(query);

        assertThat(total).isEqualTo(3L);
        verify(bookingOrderRepository).countByUserId(
                UserId.of(1001L),
                BookingStatus.PENDING_PAYMENT
        );
    }

    private BookingOrder pendingOrder() {
        return BookingOrder.place(
                BookingId.of(5001L),
                BookingNumber.of(
                        "55555555-5555-5555-5555-555555555555"
                ),
                BookingRequestNumber.of("request-5001"),
                UserId.of(1001L),
                TripReference.of(2001L),
                SeatNumber.of("A01"),
                BookingAmount.of("5.50"),
                EXPIRES_AT,
                CREATED_AT
        );
    }
}
