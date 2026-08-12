package com.schoolbus.booking.application.booking;

import com.schoolbus.booking.domain.order.BookingOrder;
import com.schoolbus.booking.domain.order.BookingOrderRepository;
import com.schoolbus.booking.domain.order.BookingStatus;
import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;
import com.schoolbus.shared.domain.identity.UserId;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Objects;

@Service
@Profile("!test")
public class BookingCancellationApplicationService {

    private final BookingOrderRepository bookingOrderRepository;
    private final BookingCancellationTransaction cancellationTransaction;
    private final Clock clock;

    public BookingCancellationApplicationService(
            BookingOrderRepository bookingOrderRepository,
            BookingCancellationTransaction cancellationTransaction,
            Clock clock
    ) {
        this.bookingOrderRepository = Objects.requireNonNull(
                bookingOrderRepository,
                "bookingOrderRepository must not be null"
        );
        this.cancellationTransaction = Objects.requireNonNull(
                cancellationTransaction,
                "cancellationTransaction must not be null"
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public BookingCancellationView cancelMyBooking(
            CancelMyBookingCommand command
    ) {
        CancelMyBookingCommand validatedCommand = Objects.requireNonNull(
                command,
                "command must not be null"
        );
        UserId userId = UserId.of(validatedCommand.userId());
        BookingOrder order = bookingOrderRepository
                .findByBookingNumber(validatedCommand.toBookingNumber())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.PAYMENT_BOOKING_NOT_FOUND
                ));

        if (!order.userId().equals(userId)) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_BOOKING_NOT_FOUND
            );
        }
        if (order.status() == BookingStatus.CANCELLED) {
            return BookingCancellationView.from(order);
        }
        if (order.status() == BookingStatus.PAID) {
            throw new BookingNotCancellableException();
        }

        try {
            BookingOrder cancelled = cancellationTransaction.cancelOne(
                    order.bookingId(),
                    userId,
                    clock.instant()
            );
            return BookingCancellationView.from(cancelled);
        } catch (OptimisticLockingFailureException
                 | BookingCancellationConflictException exception) {
            BookingOrder latest = bookingOrderRepository
                    .findByBookingNumber(validatedCommand.toBookingNumber())
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.PAYMENT_BOOKING_NOT_FOUND
                    ));
            if (!latest.userId().equals(userId)) {
                throw new BusinessException(
                        ErrorCode.PAYMENT_BOOKING_NOT_FOUND
                );
            }
            if (latest.status() == BookingStatus.CANCELLED) {
                return BookingCancellationView.from(latest);
            }
            throw exception instanceof OptimisticLockingFailureException
                    ? (OptimisticLockingFailureException) exception
                    : new OptimisticLockingFailureException(
                            "booking cancellation conflicted with another update"
                    );
        }
    }
}
