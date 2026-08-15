package com.schoolbus.booking.application.booking;

import com.schoolbus.booking.domain.order.BookingAmount;
import com.schoolbus.booking.domain.order.BookingId;
import com.schoolbus.booking.domain.order.BookingNumber;
import com.schoolbus.booking.domain.order.BookingOrder;
import com.schoolbus.booking.domain.order.BookingOrderRepository;
import com.schoolbus.booking.domain.order.BookingRequestNumber;
import com.schoolbus.booking.domain.order.SeatNumber;
import com.schoolbus.booking.domain.trip.PublicTripNumber;
import com.schoolbus.booking.domain.trip.TripReference;
import com.schoolbus.shared.domain.identity.UserId;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BookingExpirationApplicationServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-08-08T00:15:00Z");
    private static final PublicTripNumber TRIP_NUMBER =
            PublicTripNumber.of(
                    "22222222-2222-2222-2222-222222222222"
            );

    @Test
    void shouldProcessBatchAndCountOptimisticConflicts() {
        BookingOrderRepository repository =
                mock(BookingOrderRepository.class);
        BookingExpirationTransaction transaction =
                mock(BookingExpirationTransaction.class);
        BookingOrder first = order(5001L, "request-5001");
        BookingOrder second = order(5002L, "request-5002");
        when(repository.findExpiredPendingOrders(NOW, 100))
                .thenReturn(List.of(first, second));
        when(transaction.expireOne(first.bookingId(), NOW))
                .thenReturn(true);
        when(transaction.expireOne(second.bookingId(), NOW))
                .thenThrow(new OptimisticLockingFailureException(
                        "conflict"
                ));
        BookingExpirationApplicationService service =
                new BookingExpirationApplicationService(
                        repository,
                        transaction,
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        100
                );

        BookingExpirationResult result =
                service.expireDueBookings();

        assertThat(result.scanned()).isEqualTo(2);
        assertThat(result.expired()).isEqualTo(1);
        assertThat(result.conflicts()).isEqualTo(1);
    }

    private BookingOrder order(long id, String requestNumber) {
        return BookingOrder.place(
                BookingId.of(id),
                new BookingNumber(java.util.UUID.randomUUID()),
                BookingRequestNumber.of(requestNumber),
                UserId.of(id),
                TripReference.of(2001L),
                TRIP_NUMBER,
                SeatNumber.of("A01"),
                BookingAmount.of("5.50"),
                NOW,
                NOW.minusSeconds(900)
        );
    }
}
