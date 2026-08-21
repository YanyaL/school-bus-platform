package com.schoolbus.booking.application.booking;

import com.schoolbus.booking.config.ConditionalOnEmbeddedBooking;

import com.schoolbus.booking.domain.trip.PublicTripNumber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

@ConditionalOnEmbeddedBooking
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
        return createBookingOutcome(command).result();
    }

    public CreateBookingOutcome createBookingOutcome(
            CreateBookingCommand command
    ) {
        CreateBookingCommand validatedCommand = Objects.requireNonNull(
                command,
                "command must not be null"
        );
        Optional<CreateBookingResult> existing =
                creationTransaction.findIdempotentResult(
                        validatedCommand
                );
        if (existing.isPresent()) {
            return new CreateBookingOutcome(
                    existing.orElseThrow(),
                    true
            );
        }

        for (int attempt = 1; attempt <= maximumAttempts; attempt++) {
            try {
                return new CreateBookingOutcome(
                        creationTransaction.createOnce(
                                validatedCommand
                        ),
                        false
                );
            } catch (OptimisticLockingFailureException exception) {
                if (attempt == maximumAttempts) {
                    throw new BookingConcurrencyException(
                            PublicTripNumber.of(
                                    validatedCommand.tripNumber()
                            )
                    );
                }
                Thread.onSpinWait();
            } catch (SeatAlreadyReservedException
                     | BookingAlreadyExistsException exception) {
                Optional<CreateBookingResult> idempotentResult =
                        creationTransaction.findIdempotentResult(
                                validatedCommand
                        );
                if (idempotentResult.isPresent()) {
                    return new CreateBookingOutcome(
                            idempotentResult.orElseThrow(),
                            true
                    );
                }
                throw exception;
            } catch (DuplicateKeyException exception) {
                return new CreateBookingOutcome(
                        creationTransaction
                                .resolveUniqueConstraintConflict(
                                        validatedCommand
                                ),
                        true
                );
            }
        }
        throw new IllegalStateException("unreachable booking retry state");
    }
}
