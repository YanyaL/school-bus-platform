package com.schoolbus.booking.application.booking;

import com.schoolbus.booking.config.ConditionalOnEmbeddedBooking;

import com.schoolbus.booking.domain.order.BookingNumber;
import com.schoolbus.shared.domain.identity.UserId;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Objects;

@ConditionalOnEmbeddedBooking
@Service
@Profile("!test")
public class BookingCancellationApplicationService {

    private final BookingCancellationTransaction cancellationTransaction;

    public BookingCancellationApplicationService(
            BookingCancellationTransaction cancellationTransaction
    ) {
        this.cancellationTransaction = Objects.requireNonNull(
                cancellationTransaction,
                "cancellationTransaction must not be null"
        );
    }

    public CancelBookingResult cancelMyBooking(
            long userId,
            String bookingNumber
    ) {
        return cancellationTransaction.cancelOne(
                UserId.of(userId),
                BookingNumber.of(bookingNumber)
        );
    }
}
