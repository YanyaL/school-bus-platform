package com.schoolbus.booking.application.booking;

import com.schoolbus.booking.domain.trip.TripReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

@Service
@Profile("!test")
public class BookingApplicationService {

    private final BookingCreationTransaction creationTransaction;
    private final int maximumAttempts;

    public BookingApplicationService(
            BookingCreationTransaction creationTransaction,
            @Value("${school-bus.booking.maximum-attempts:3}")
            int maximumAttempts
    ) {
        this.creationTransaction = Objects.requireNonNull(
                creationTransaction,
                "creationTransaction must not be null"
        );
        if (maximumAttempts <= 0) {
            throw new IllegalArgumentException(
                    "maximumAttempts must be positive"
            );
        }
        this.maximumAttempts = maximumAttempts;
    }

    public CreateBookingResult createBooking(
            CreateBookingCommand command
    ) {
        CreateBookingCommand validatedCommand = Objects.requireNonNull(
                command,
                "command must not be null"
        );
        for (int attempt = 1; attempt <= maximumAttempts; attempt++) {
            try {
                return creationTransaction.createOnce(
                        validatedCommand
                );
            } catch (OptimisticLockingFailureException exception) {
                if (attempt == maximumAttempts) {
                    throw new BookingConcurrencyException(
                            TripReference.of(validatedCommand.tripId())
                    );
                }
                Thread.onSpinWait();
            } catch (SeatAlreadyReservedException
                     | BookingAlreadyExistsException exception) {
                Optional<CreateBookingResult> existing =
                        creationTransaction.findIdempotentResult(
                                validatedCommand
                        );
                if (existing.isPresent()) {
                    return existing.orElseThrow();
                }
                throw exception;
            } catch (DuplicateKeyException exception) {
                return creationTransaction
                        .resolveUniqueConstraintConflict(
                                validatedCommand
                        );
            }
        }
        throw new IllegalStateException("unreachable booking retry state");
    }
}
