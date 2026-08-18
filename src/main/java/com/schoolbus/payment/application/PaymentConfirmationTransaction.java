package com.schoolbus.payment.application;

import com.schoolbus.booking.application.booking.SeatSaleRequest;
import com.schoolbus.booking.application.booking.TripSeatReservationPort;
import com.schoolbus.booking.domain.order.BookingAmount;
import com.schoolbus.booking.domain.order.BookingNumber;
import com.schoolbus.booking.domain.order.BookingOrder;
import com.schoolbus.booking.domain.order.BookingOrderRepository;
import com.schoolbus.booking.domain.order.BookingStatus;
import com.schoolbus.booking.domain.order.PaymentReference;
import com.schoolbus.payment.domain.PaymentIdGenerator;
import com.schoolbus.payment.domain.PaymentNumber;
import com.schoolbus.payment.domain.PaymentRecord;
import com.schoolbus.payment.domain.PaymentRecordRepository;
import com.schoolbus.payment.domain.PaymentRequestNumber;
import com.schoolbus.payment.config.ConditionalOnEmbeddedPayment;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Service
@Profile("!test")
@ConditionalOnEmbeddedPayment
public class PaymentConfirmationTransaction {

    private static final String PAYMENT_WINDOW_EXPIRED = "PAYMENT_WINDOW_EXPIRED";
    private static final String ORDER_ALREADY_FINALIZED = "ORDER_ALREADY_FINALIZED";
    private static final String SEAT_LOCK_LOST = "SEAT_LOCK_LOST";

    private final PaymentRecordRepository paymentRecordRepository;
    private final BookingOrderRepository bookingOrderRepository;
    private final TripSeatReservationPort tripSeatReservationPort;
    private final PaymentRefundOutboxPort refundOutboxPort;
    private final PaymentIdGenerator paymentIdGenerator;
    private final Clock clock;

    public PaymentConfirmationTransaction(
            PaymentRecordRepository paymentRecordRepository,
            BookingOrderRepository bookingOrderRepository,
            TripSeatReservationPort tripSeatReservationPort,
            PaymentRefundOutboxPort refundOutboxPort,
            PaymentIdGenerator paymentIdGenerator,
            Clock clock
    ) {
        this.paymentRecordRepository = Objects.requireNonNull(paymentRecordRepository);
        this.bookingOrderRepository = Objects.requireNonNull(bookingOrderRepository);
        this.tripSeatReservationPort = Objects.requireNonNull(tripSeatReservationPort);
        this.refundOutboxPort = Objects.requireNonNull(refundOutboxPort);
        this.paymentIdGenerator = Objects.requireNonNull(paymentIdGenerator);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ConfirmPaymentResult confirmOnce(ConfirmPaymentCommand command) {
        ParsedCommand parsed = parse(command);
        Optional<ConfirmPaymentResult> existing = findExisting(parsed);
        if (existing.isPresent()) {
            return existing.orElseThrow();
        }

        BookingOrder order = bookingOrderRepository
                .findByBookingNumber(parsed.bookingNumber())
                .orElseThrow(() -> new PaymentBookingNotFoundException(parsed.bookingNumber()));
        ensureAmountMatches(order, parsed.amount());
        Instant now = clock.instant();

        if (order.status() != BookingStatus.PENDING_PAYMENT) {
            return recordRefundPending(parsed, ORDER_ALREADY_FINALIZED, now);
        }
        if (!parsed.paidAt().isBefore(order.expiresAt())) {
            return recordRefundPending(parsed, PAYMENT_WINDOW_EXPIRED, now);
        }

        boolean sold = tripSeatReservationPort.confirmSeatSold(
                new SeatSaleRequest(
                        order.tripReference(),
                        order.seatNumber(),
                        order.bookingNumber(),
                        now
                )
        );
        if (!sold) {
            return recordRefundPending(parsed, SEAT_LOCK_LOST, now);
        }

        PaymentRecord paymentRecord = PaymentRecord.succeeded(
                paymentIdGenerator.nextId(),
                parsed.paymentNumber(),
                parsed.requestNumber(),
                parsed.bookingNumber(),
                parsed.amount(),
                parsed.paidAt(),
                now
        );
        paymentRecordRepository.save(paymentRecord);
        order.confirmPayment(
                PaymentReference.of(parsed.paymentNumber().toString()),
                parsed.paidAt(),
                now
        );
        bookingOrderRepository.save(order);
        return ConfirmPaymentResult.from(paymentRecord);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ConfirmPaymentResult resolveDuplicate(ConfirmPaymentCommand command) {
        ParsedCommand parsed = parse(command);
        return findExisting(parsed)
                .orElseThrow(PaymentRequestConflictException::new);
    }

    private ConfirmPaymentResult recordRefundPending(
            ParsedCommand command,
            String reason,
            Instant now
    ) {
        PaymentRecord paymentRecord = PaymentRecord.refundPending(
                paymentIdGenerator.nextId(),
                command.paymentNumber(),
                command.requestNumber(),
                command.bookingNumber(),
                command.amount(),
                reason,
                command.paidAt(),
                now
        );
        paymentRecordRepository.save(paymentRecord);
        refundOutboxPort.append(new RefundRequiredEvent(
                command.paymentNumber(),
                command.bookingNumber(),
                command.amount(),
                reason,
                command.paidAt(),
                now
        ));
        return ConfirmPaymentResult.from(paymentRecord);
    }

    private Optional<ConfirmPaymentResult> findExisting(ParsedCommand command) {
        Optional<PaymentRecord> byRequest = paymentRecordRepository
                .findByRequestNumber(command.requestNumber());
        if (byRequest.isPresent()) {
            PaymentRecord existing = byRequest.orElseThrow();
            ensureSameCallback(existing, command, true);
            return Optional.of(ConfirmPaymentResult.from(existing));
        }
        Optional<PaymentRecord> byPayment = paymentRecordRepository
                .findByPaymentNumber(command.paymentNumber());
        if (byPayment.isPresent()) {
            PaymentRecord existing = byPayment.orElseThrow();
            ensureSameCallback(existing, command, false);
            return Optional.of(ConfirmPaymentResult.from(existing));
        }
        return Optional.empty();
    }

    private void ensureSameCallback(
            PaymentRecord existing,
            ParsedCommand command,
            boolean requireSamePaymentNumber
    ) {
        boolean same = existing.bookingNumber().equals(command.bookingNumber())
                && existing.amount().amount().compareTo(command.amount().amount()) == 0
                && existing.completedAt().equals(command.paidAt())
                && (!requireSamePaymentNumber
                    || existing.paymentNumber().equals(command.paymentNumber()));
        if (!same) {
            throw new PaymentRequestConflictException();
        }
    }

    private void ensureAmountMatches(BookingOrder order, BookingAmount amount) {
        if (order.amount().amount().compareTo(amount.amount()) != 0) {
            throw new PaymentAmountMismatchException();
        }
    }

    private ParsedCommand parse(ConfirmPaymentCommand command) {
        ConfirmPaymentCommand validated = Objects.requireNonNull(command, "command must not be null");
        return new ParsedCommand(
                PaymentRequestNumber.of(validated.requestNumber()),
                PaymentNumber.of(validated.paymentNumber()),
                BookingNumber.of(validated.bookingNumber()),
                new BookingAmount(validated.amount()),
                validated.paidAt()
        );
    }

    private record ParsedCommand(
            PaymentRequestNumber requestNumber,
            PaymentNumber paymentNumber,
            BookingNumber bookingNumber,
            BookingAmount amount,
            Instant paidAt
    ) {
    }
}
