package com.schoolbus.bookingservice.application.booking;

import com.schoolbus.bookingservice.domain.inventory.SeatInventory;
import com.schoolbus.bookingservice.domain.inventory.SeatInventoryRepository;
import com.schoolbus.bookingservice.domain.order.BookingAmount;
import com.schoolbus.bookingservice.domain.order.BookingId;
import com.schoolbus.bookingservice.domain.order.BookingIdGenerator;
import com.schoolbus.bookingservice.domain.order.BookingNumber;
import com.schoolbus.bookingservice.domain.order.BookingNumberGenerator;
import com.schoolbus.bookingservice.domain.order.BookingOrder;
import com.schoolbus.bookingservice.domain.order.BookingOrderRepository;
import com.schoolbus.bookingservice.domain.order.BookingRequestNumber;
import com.schoolbus.bookingservice.domain.order.BookingStatus;
import com.schoolbus.bookingservice.domain.order.SeatNumber;
import com.schoolbus.bookingservice.domain.trip.PublicTripNumber;
import com.schoolbus.bookingservice.domain.trip.TripReference;
import com.schoolbus.bookingservice.shared.domain.identity.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingCreationTransactionTest {

    private static final Instant NOW =
            Instant.parse("2026-08-08T00:00:00Z");
    private static final Instant DEADLINE =
            Instant.parse("2026-08-08T01:00:00Z");
    private static final Instant DEPARTURE =
            Instant.parse("2026-08-08T02:00:00Z");
    private static final TripReference TRIP =
            TripReference.of(2001L);
    private static final String TRIP_NUMBER_VALUE =
            "22222222-2222-2222-2222-222222222222";
    private static final PublicTripNumber TRIP_NUMBER =
            PublicTripNumber.of(TRIP_NUMBER_VALUE);
    private static final BookingNumber BOOKING_NUMBER =
            BookingNumber.of(
                    "55555555-5555-5555-5555-555555555555"
            );

    private BookableTripGateway tripGateway;
    private TripSeatReservationPort seatReservationPort;
    private SeatInventoryRepository inventoryRepository;
    private BookingOrderRepository orderRepository;
    private BookingIdGenerator idGenerator;
    private BookingNumberGenerator numberGenerator;
    private BookingExpirationOutboxPort expirationOutboxPort;
    private BookingCreationTransaction transaction;

    @BeforeEach
    void setUp() {
        tripGateway = mock(BookableTripGateway.class);
        seatReservationPort = mock(TripSeatReservationPort.class);
        inventoryRepository = mock(SeatInventoryRepository.class);
        orderRepository = mock(BookingOrderRepository.class);
        idGenerator = mock(BookingIdGenerator.class);
        numberGenerator = mock(BookingNumberGenerator.class);
        expirationOutboxPort = mock(BookingExpirationOutboxPort.class);
        transaction = new BookingCreationTransaction(
                tripGateway,
                seatReservationPort,
                inventoryRepository,
                orderRepository,
                idGenerator,
                numberGenerator,
                expirationOutboxPort,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(15)
        );
    }

    @Test
    void shouldLockSeatReserveInventoryAndCreatePendingOrder() {
        prepareSuccessfulCreation(DEADLINE);

        CreateBookingResult result = transaction.createOnce(command());

        assertThat(result.bookingId()).isEqualTo(5001L);
        assertThat(result.status())
                .isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(result.expiresAt())
                .isEqualTo(NOW.plus(Duration.ofMinutes(15)));

        ArgumentCaptor<SeatLockRequest> lockCaptor =
                ArgumentCaptor.forClass(SeatLockRequest.class);
        verify(seatReservationPort).tryLockSeat(
                lockCaptor.capture()
        );
        assertThat(lockCaptor.getValue().bookingNumber())
                .isEqualTo(BOOKING_NUMBER);
        assertThat(lockCaptor.getValue().lockExpiresAt())
                .isEqualTo(result.expiresAt());

        ArgumentCaptor<SeatInventory> inventoryCaptor =
                ArgumentCaptor.forClass(SeatInventory.class);
        verify(inventoryRepository).save(
                inventoryCaptor.capture()
        );
        assertThat(inventoryCaptor.getValue().availableSeats())
                .isEqualTo(9);
        assertThat(inventoryCaptor.getValue().version())
                .isEqualTo(1L);

        ArgumentCaptor<BookingOrder> orderCaptor =
                ArgumentCaptor.forClass(BookingOrder.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().amount())
                .isEqualTo(BookingAmount.of("5.50"));
        assertThat(orderCaptor.getValue().tripNumber())
                .isEqualTo(TRIP_NUMBER);
        assertThat(orderCaptor.getValue().tripReference())
                .isEqualTo(TRIP);
        assertThat(result.tripNumber()).isEqualTo(TRIP_NUMBER_VALUE);

        ArgumentCaptor<BookingPaymentDeadlineEvent> eventCaptor =
                ArgumentCaptor.forClass(
                        BookingPaymentDeadlineEvent.class
                );
        verify(expirationOutboxPort).append(eventCaptor.capture());
        assertThat(eventCaptor.getValue().bookingId())
                .isEqualTo(BookingId.of(5001L));
        assertThat(eventCaptor.getValue().expiresAt())
                .isEqualTo(result.expiresAt());
    }

    @Test
    void shouldCapPaymentExpirationAtBookingDeadline() {
        Instant closeDeadline = NOW.plus(Duration.ofMinutes(10));
        prepareSuccessfulCreation(closeDeadline);

        CreateBookingResult result = transaction.createOnce(command());

        assertThat(result.expiresAt()).isEqualTo(closeDeadline);
    }

    @Test
    void shouldReturnExistingOrderForSameRequestNumber() {
        BookingOrder existing = existingOrder();
        when(orderRepository.findByRequestNumber(
                BookingRequestNumber.of("request-5001")
        )).thenReturn(Optional.of(existing));

        CreateBookingResult result = transaction.createOnce(command());

        assertThat(result.bookingId())
                .isEqualTo(existing.bookingId().value());
        verify(tripGateway, never()).findByTripNumber(any());
        verify(seatReservationPort, never()).tryLockSeat(any());
        verify(inventoryRepository, never()).save(any());
        verify(expirationOutboxPort, never()).append(any());
    }

    @Test
    void shouldRejectReuseOfRequestNumberForDifferentBooking() {
        BookingOrder existing = existingOrder();
        when(orderRepository.findByRequestNumber(
                BookingRequestNumber.of("request-5001")
        )).thenReturn(Optional.of(existing));
        CreateBookingCommand conflictingCommand =
                new CreateBookingCommand(
                        1001L,
                        TRIP_NUMBER_VALUE,
                        "A02",
                        "request-5001"
                );

        assertThatThrownBy(
                () -> transaction.createOnce(conflictingCommand)
        ).isInstanceOf(BookingRequestConflictException.class);
    }

    @Test
    void shouldRejectUnknownTripNumber() {
        when(orderRepository.findByRequestNumber(any()))
                .thenReturn(Optional.empty());
        when(tripGateway.findByTripNumber(TRIP_NUMBER))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> transaction.createOnce(command()))
                .isInstanceOf(TripNotBookableException.class);

        verify(seatReservationPort, never()).tryLockSeat(any());
    }

    @Test
    void shouldNotChangeInventoryWhenSeatCannotBeLocked() {
        prepareSuccessfulCreation(DEADLINE);
        when(seatReservationPort.tryLockSeat(any()))
                .thenReturn(false);

        assertThatThrownBy(() -> transaction.createOnce(command()))
                .isInstanceOf(SeatAlreadyReservedException.class);

        verify(inventoryRepository, never()).save(any());
        verify(orderRepository, never()).save(any(BookingOrder.class));
        verify(expirationOutboxPort, never()).append(any());
    }

    private void prepareSuccessfulCreation(Instant deadline) {
        when(orderRepository.findByRequestNumber(any()))
                .thenReturn(Optional.empty());
        when(tripGateway.findByTripNumber(TRIP_NUMBER))
                .thenReturn(Optional.of(new BookableTripSnapshot(
                        TRIP,
                        TRIP_NUMBER,
                        BookingAmount.of("5.50"),
                        DEPARTURE,
                        deadline,
                        true
                )));
        when(orderRepository.existsActiveByUserIdAndTripReference(
                UserId.of(1001L),
                TRIP
        )).thenReturn(false);
        when(inventoryRepository.findByTripReference(TRIP))
                .thenReturn(Optional.of(SeatInventory.initialize(
                        TRIP,
                        10,
                        NOW.minusSeconds(60)
                )));
        when(idGenerator.nextId()).thenReturn(BookingId.of(5001L));
        when(numberGenerator.nextNumber()).thenReturn(BOOKING_NUMBER);
        when(seatReservationPort.tryLockSeat(any()))
                .thenReturn(true);
        when(inventoryRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private CreateBookingCommand command() {
        return new CreateBookingCommand(
                1001L,
                TRIP_NUMBER_VALUE,
                "A01",
                "request-5001"
        );
    }

    private BookingOrder existingOrder() {
        return BookingOrder.place(
                BookingId.of(5001L),
                BOOKING_NUMBER,
                BookingRequestNumber.of("request-5001"),
                UserId.of(1001L),
                TRIP,
                TRIP_NUMBER,
                SeatNumber.of("A01"),
                BookingAmount.of("5.50"),
                NOW.plus(Duration.ofMinutes(15)),
                NOW
        );
    }
}
