package com.schoolbus.bookingservice.application.booking;

import com.schoolbus.bookingservice.domain.order.BookingNumber;
import com.schoolbus.bookingservice.shared.domain.identity.UserId;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Objects;

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
