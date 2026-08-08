package com.schoolbus.booking.application.booking;

import com.schoolbus.booking.domain.order.BookingOrder;
import com.schoolbus.booking.domain.order.BookingOrderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
@Profile("!test")
public class BookingExpirationApplicationService {
    private final BookingOrderRepository bookingOrderRepository;
    private final BookingExpirationTransaction expirationTransaction;
    private final Clock clock;
    private final int batchSize;

    public BookingExpirationApplicationService(
            BookingOrderRepository bookingOrderRepository,
            BookingExpirationTransaction expirationTransaction,
            Clock clock,
            @Value("${school-bus.booking.expiration.batch-size:100}")
            int batchSize
    ) {
        this.bookingOrderRepository = Objects.requireNonNull(
                bookingOrderRepository,
                "bookingOrderRepository must not be null"
        );
        this.expirationTransaction = Objects.requireNonNull(
                expirationTransaction,
                "expirationTransaction must not be null"
        );
        this.clock = Objects.requireNonNull(
                clock,
                "clock must not be null"
        );
        if (batchSize <= 0) {
            throw new IllegalArgumentException(
                    "batchSize must be positive"
            );
        }
        this.batchSize = batchSize;
    }

    public BookingExpirationResult expireDueBookings() {
        Instant now = clock.instant();
        List<BookingOrder> candidates = bookingOrderRepository
                .findExpiredPendingOrders(now, batchSize);
        int expired = 0;
        int conflicts = 0;
        for (BookingOrder candidate : candidates) {
            try {
                if (expirationTransaction.expireOne(
                        candidate.bookingId(),
                        now
                )) {
                    expired++;
                }
            } catch (OptimisticLockingFailureException exception) {
                conflicts++;
            }
        }
        return new BookingExpirationResult(
                candidates.size(),
                expired,
                conflicts
        );
    }
}
