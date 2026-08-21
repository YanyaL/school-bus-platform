package com.schoolbus.booking.application.booking;

import com.schoolbus.booking.config.ConditionalOnEmbeddedBooking;

import com.schoolbus.booking.domain.inventory.SeatInventory;
import com.schoolbus.booking.domain.inventory.SeatInventoryRepository;
import com.schoolbus.booking.domain.order.BookingId;
import com.schoolbus.booking.domain.order.BookingIdGenerator;
import com.schoolbus.booking.domain.order.BookingNumber;
import com.schoolbus.booking.domain.order.BookingNumberGenerator;
import com.schoolbus.booking.domain.order.BookingOrder;
import com.schoolbus.booking.domain.order.BookingOrderRepository;
import com.schoolbus.booking.domain.order.BookingRequestNumber;
import com.schoolbus.booking.domain.order.SeatNumber;
import com.schoolbus.booking.domain.trip.PublicTripNumber;
import com.schoolbus.booking.domain.trip.TripReference;
import com.schoolbus.shared.domain.identity.UserId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@ConditionalOnEmbeddedBooking
@Service
@Profile("!test")
public class BookingCreationTransaction {

    private final BookableTripGateway bookableTripGateway;
    private final TripSeatReservationPort tripSeatReservationPort;
    private final SeatInventoryRepository seatInventoryRepository;
    private final BookingOrderRepository bookingOrderRepository;
    private final BookingIdGenerator bookingIdGenerator;
    private final BookingNumberGenerator bookingNumberGenerator;
    private final BookingExpirationOutboxPort expirationOutboxPort;
    private final Clock clock;
    private final Duration paymentWindow;

    public BookingCreationTransaction(
            BookableTripGateway bookableTripGateway,
            TripSeatReservationPort tripSeatReservationPort,
            SeatInventoryRepository seatInventoryRepository,
            BookingOrderRepository bookingOrderRepository,
            BookingIdGenerator bookingIdGenerator,
            BookingNumberGenerator bookingNumberGenerator,
            BookingExpirationOutboxPort expirationOutboxPort,
            Clock clock,
            @Value("${school-bus.booking.payment-window:PT15M}")
            Duration paymentWindow
    ) {
        this.bookableTripGateway = Objects.requireNonNull(
                bookableTripGateway,
                "bookableTripGateway must not be null"
        );
        this.tripSeatReservationPort = Objects.requireNonNull(
                tripSeatReservationPort,
                "tripSeatReservationPort must not be null"
        );
        this.seatInventoryRepository = Objects.requireNonNull(
                seatInventoryRepository,
                "seatInventoryRepository must not be null"
        );
        this.bookingOrderRepository = Objects.requireNonNull(
                bookingOrderRepository,
                "bookingOrderRepository must not be null"
        );
        this.bookingIdGenerator = Objects.requireNonNull(
                bookingIdGenerator,
                "bookingIdGenerator must not be null"
        );
        this.bookingNumberGenerator = Objects.requireNonNull(
                bookingNumberGenerator,
                "bookingNumberGenerator must not be null"
        );
        this.expirationOutboxPort = Objects.requireNonNull(
                expirationOutboxPort,
                "expirationOutboxPort must not be null"
        );
        this.clock = Objects.requireNonNull(
                clock,
                "clock must not be null"
        );
        this.paymentWindow = validatePaymentWindow(paymentWindow);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CreateBookingResult createOnce(
            CreateBookingCommand command
    ) {
        CreateBookingCommand validatedCommand = Objects.requireNonNull(
                command,
                "command must not be null"
        );
        BookingRequestNumber requestNumber = BookingRequestNumber.of(
                validatedCommand.requestNumber()
        );
        Optional<CreateBookingResult> existing = findIdempotentResult(
                validatedCommand,
                requestNumber
        );
        if (existing.isPresent()) {
            return existing.orElseThrow();
        }

        Instant now = clock.instant();
        UserId userId = UserId.of(validatedCommand.userId());
        PublicTripNumber tripNumber = PublicTripNumber.of(
                validatedCommand.tripNumber()
        );
        SeatNumber seatNumber = SeatNumber.of(
                validatedCommand.seatNumber()
        );
        BookableTripSnapshot trip = bookableTripGateway
                .findByTripNumber(tripNumber)
                .filter(snapshot -> snapshot.canBookAt(now))
                .orElseThrow(
                        () -> new TripNotBookableException(tripNumber)
                );
        TripReference tripReference = trip.tripReference();

        if (bookingOrderRepository
                .existsActiveByUserIdAndTripReference(
                        userId,
                        tripReference
                )) {
            throw new BookingAlreadyExistsException(
                    userId,
                    tripReference
            );
        }

        SeatInventory inventory = seatInventoryRepository
                .findByTripReference(tripReference)
                .orElseThrow(
                        () -> new SeatInventoryNotFoundException(
                                tripReference
                        )
                );
        BookingId bookingId = bookingIdGenerator.nextId();
        BookingNumber bookingNumber = bookingNumberGenerator.nextNumber();
        Instant expiresAt = calculateExpiration(
                now,
                trip.bookingDeadline()
        );

        boolean locked = tripSeatReservationPort.tryLockSeat(
                new SeatLockRequest(
                        tripReference,
                        seatNumber,
                        bookingNumber,
                        userId,
                        expiresAt,
                        now
                )
        );
        if (!locked) {
            throw new SeatAlreadyReservedException(
                    tripReference,
                    seatNumber
            );
        }

        inventory.reserve(now);
        seatInventoryRepository.save(inventory);

        BookingOrder order = BookingOrder.place(
                bookingId,
                bookingNumber,
                requestNumber,
                userId,
                tripReference,
                trip.tripNumber(),
                seatNumber,
                trip.price(),
                expiresAt,
                now
        );
        BookingOrder savedOrder = bookingOrderRepository.save(order);
        expirationOutboxPort.append(
                new BookingPaymentDeadlineEvent(
                        savedOrder.bookingId(),
                        savedOrder.bookingNumber(),
                        savedOrder.expiresAt(),
                        now,
                        savedOrder.version()
                )
        );
        return CreateBookingResult.from(savedOrder);
    }

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            readOnly = true
    )
    public Optional<CreateBookingResult> findIdempotentResult(
            CreateBookingCommand command
    ) {
        CreateBookingCommand validatedCommand = Objects.requireNonNull(
                command,
                "command must not be null"
        );
        return findIdempotentResult(
                validatedCommand,
                BookingRequestNumber.of(validatedCommand.requestNumber())
        );
    }

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            readOnly = true
    )
    public CreateBookingResult resolveUniqueConstraintConflict(
            CreateBookingCommand command
    ) {
        CreateBookingCommand validatedCommand = Objects.requireNonNull(
                command,
                "command must not be null"
        );
        Optional<CreateBookingResult> existing = findIdempotentResult(
                validatedCommand
        );
        if (existing.isPresent()) {
            return existing.orElseThrow();
        }

        UserId userId = UserId.of(validatedCommand.userId());
        PublicTripNumber tripNumber = PublicTripNumber.of(
                validatedCommand.tripNumber()
        );
        TripReference tripReference = bookableTripGateway
                .findByTripNumber(tripNumber)
                .map(BookableTripSnapshot::tripReference)
                .orElseThrow(
                        () -> new TripNotBookableException(tripNumber)
                );
        if (bookingOrderRepository
                .existsActiveByUserIdAndTripReference(
                        userId,
                        tripReference
                )) {
            throw new BookingAlreadyExistsException(
                    userId,
                    tripReference
            );
        }
        throw new BookingConcurrencyException(tripNumber);
    }

    private Optional<CreateBookingResult> findIdempotentResult(
            CreateBookingCommand command,
            BookingRequestNumber requestNumber
    ) {
        return bookingOrderRepository
                .findByRequestNumber(requestNumber)
                .map(order -> {
                    ensureSameRequest(order, command, requestNumber);
                    return CreateBookingResult.from(order);
                });
    }

    private void ensureSameRequest(
            BookingOrder order,
            CreateBookingCommand command,
            BookingRequestNumber requestNumber
    ) {
        boolean sameRequest = order.userId().value() == command.userId()
                && order.tripNumber().toString().equals(
                        command.tripNumber()
                )
                && order.seatNumber().value().equals(
                        command.seatNumber()
                );
        if (!sameRequest) {
            throw new BookingRequestConflictException(requestNumber);
        }
    }

    private Instant calculateExpiration(
            Instant now,
            Instant bookingDeadline
    ) {
        Instant paymentExpiration = now.plus(paymentWindow);
        return paymentExpiration.isBefore(bookingDeadline)
                ? paymentExpiration
                : bookingDeadline;
    }

    private Duration validatePaymentWindow(Duration duration) {
        Duration checkedDuration = Objects.requireNonNull(
                duration,
                "paymentWindow must not be null"
        );
        if (checkedDuration.isZero() || checkedDuration.isNegative()) {
            throw new IllegalArgumentException(
                    "paymentWindow must be positive"
            );
        }
        return checkedDuration;
    }
}
