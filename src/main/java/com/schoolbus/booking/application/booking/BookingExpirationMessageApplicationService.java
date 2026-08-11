package com.schoolbus.booking.application.booking;

import com.schoolbus.booking.domain.order.BookingId;
import com.schoolbus.booking.domain.order.BookingNumber;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Objects;

@Service
@Profile("!test")
public class BookingExpirationMessageApplicationService {

    private final BookingExpirationTransaction expirationTransaction;
    private final Clock clock;

    public BookingExpirationMessageApplicationService(
            BookingExpirationTransaction expirationTransaction,
            Clock clock
    ) {
        this.expirationTransaction = Objects.requireNonNull(
                expirationTransaction,
                "expirationTransaction must not be null"
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public boolean process(BookingExpirationMessage message) {
        BookingExpirationMessage validated = Objects.requireNonNull(
                message,
                "message must not be null"
        );
        return expirationTransaction.expireOne(
                BookingId.of(validated.bookingId()),
                new BookingNumber(validated.bookingNumber()),
                clock.instant()
        );
    }
}
