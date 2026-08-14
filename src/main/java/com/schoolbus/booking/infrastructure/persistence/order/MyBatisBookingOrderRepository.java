package com.schoolbus.booking.infrastructure.persistence.order;

import com.schoolbus.booking.domain.order.BookingAmount;
import com.schoolbus.booking.domain.order.BookingId;
import com.schoolbus.booking.domain.order.BookingNumber;
import com.schoolbus.booking.domain.order.BookingOrder;
import com.schoolbus.booking.domain.order.BookingOrderRepository;
import com.schoolbus.booking.domain.order.BookingRequestNumber;
import com.schoolbus.booking.domain.order.BookingStatus;
import com.schoolbus.booking.domain.order.CancellationReason;
import com.schoolbus.booking.domain.order.PaymentReference;
import com.schoolbus.booking.domain.order.SeatNumber;
import com.schoolbus.booking.domain.trip.TripReference;
import com.schoolbus.shared.domain.identity.UserId;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.List;

@Repository
@Profile("!test")
public class MyBatisBookingOrderRepository
        implements BookingOrderRepository {

    private static final ZoneOffset DATABASE_ZONE = ZoneOffset.UTC;

    private final BookingOrderMapper bookingOrderMapper;

    public MyBatisBookingOrderRepository(
            BookingOrderMapper bookingOrderMapper
    ) {
        this.bookingOrderMapper = Objects.requireNonNull(
                bookingOrderMapper,
                "bookingOrderMapper must not be null"
        );
    }

    @Override
    public BookingOrder save(BookingOrder bookingOrder) {
        BookingOrder validatedOrder = Objects.requireNonNull(
                bookingOrder,
                "bookingOrder must not be null"
        );
        BookingOrderDataObject dataObject = toDataObject(
                validatedOrder
        );

        if (validatedOrder.version() == 0L) {
            int insertedRows = bookingOrderMapper.insertOrder(
                    dataObject
            );
            if (insertedRows != 1) {
                throw new IllegalStateException(
                        "failed to insert booking order"
                );
            }
            return validatedOrder;
        }

        long expectedVersion = validatedOrder.version() - 1L;
        int updatedRows = bookingOrderMapper.updateWithVersion(
                dataObject,
                expectedVersion
        );
        if (updatedRows != 1) {
            throw new OptimisticLockingFailureException(
                    "booking order was modified by another request"
            );
        }
        return validatedOrder;
    }

    @Override
    public Optional<BookingOrder> findById(BookingId bookingId) {
        BookingId validatedId = Objects.requireNonNull(
                bookingId,
                "bookingId must not be null"
        );
        BookingOrderDataObject dataObject =
                bookingOrderMapper.selectById(validatedId.value());
        return dataObject == null
                ? Optional.empty()
                : Optional.of(toDomain(dataObject));
    }

    @Override
    public Optional<BookingOrder> findByBookingNumber(
            BookingNumber bookingNumber
    ) {
        BookingNumber validatedNumber = Objects.requireNonNull(
                bookingNumber,
                "bookingNumber must not be null"
        );
        BookingOrderDataObject dataObject =
                bookingOrderMapper.selectByOrderNo(
                        validatedNumber.toString()
                );
        return dataObject == null
                ? Optional.empty()
                : Optional.of(toDomain(dataObject));
    }

    @Override
    public Optional<BookingOrder> findByRequestNumber(
            BookingRequestNumber requestNumber
    ) {
        BookingRequestNumber validatedRequestNumber =
                Objects.requireNonNull(
                        requestNumber,
                        "requestNumber must not be null"
                );
        BookingOrderDataObject dataObject =
                bookingOrderMapper.selectByRequestNo(
                        validatedRequestNumber.value()
                );
        return dataObject == null
                ? Optional.empty()
                : Optional.of(toDomain(dataObject));
    }

    @Override
    public boolean existsActiveByUserIdAndTripReference(
            UserId userId,
            TripReference tripReference
    ) {
        UserId validatedUserId = Objects.requireNonNull(
                userId,
                "userId must not be null"
        );
        TripReference validatedTripReference =
                Objects.requireNonNull(
                        tripReference,
                        "tripReference must not be null"
                );
        return bookingOrderMapper.existsActiveByUserIdAndTripId(
                validatedUserId.value(),
                validatedTripReference.value()
        );
    }

    @Override
    public boolean existsActiveByTripReference(
            TripReference tripReference
    ) {
        TripReference validatedTripReference = Objects.requireNonNull(
                tripReference,
                "tripReference must not be null"
        );
        return bookingOrderMapper.existsActiveByTripId(
                validatedTripReference.value()
        );
    }

    @Override
    public List<BookingOrder> findActiveByTripReferenceForUpdate(
            TripReference tripReference
    ) {
        TripReference validated = Objects.requireNonNull(
                tripReference,
                "tripReference must not be null"
        );
        return bookingOrderMapper.selectActiveByTripIdForUpdate(
                        validated.value()
                )
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<BookingOrder> findExpiredPendingOrders(
            Instant expiredAt,
            int limit
    ) {
        Instant validatedExpiration = Objects.requireNonNull(
                expiredAt,
                "expiredAt must not be null"
        );
        if (limit <= 0) {
            throw new IllegalArgumentException(
                    "limit must be positive"
            );
        }
        return bookingOrderMapper.selectExpiredPendingOrders(
                        toDatabaseTime(validatedExpiration),
                        limit
                )
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<BookingOrder> findByUserId(
            UserId userId,
            BookingStatus status,
            int offset,
            int limit,
            boolean sortByCreatedAtAscending
    ) {
        UserId validatedUserId = Objects.requireNonNull(
                userId,
                "userId must not be null"
        );
        if (offset < 0) {
            throw new IllegalArgumentException(
                    "offset must not be negative"
            );
        }
        if (limit <= 0) {
            throw new IllegalArgumentException(
                    "limit must be positive"
            );
        }
        return bookingOrderMapper.selectByUserId(
                        validatedUserId.value(),
                        status == null ? null : status.name(),
                        offset,
                        limit,
                        sortByCreatedAtAscending
                )
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public long countByUserId(
            UserId userId,
            BookingStatus status
    ) {
        UserId validatedUserId = Objects.requireNonNull(
                userId,
                "userId must not be null"
        );
        return bookingOrderMapper.countByUserId(
                validatedUserId.value(),
                status == null ? null : status.name()
        );
    }

    private BookingOrderDataObject toDataObject(
            BookingOrder bookingOrder
    ) {
        BookingOrderDataObject dataObject =
                new BookingOrderDataObject();
        dataObject.setId(bookingOrder.bookingId().value());
        dataObject.setOrderNo(
                bookingOrder.bookingNumber().toString()
        );
        dataObject.setRequestNo(
                bookingOrder.requestNumber().value()
        );
        dataObject.setUserId(bookingOrder.userId().value());
        dataObject.setTripId(
                bookingOrder.tripReference().value()
        );
        dataObject.setSeatNumber(
                bookingOrder.seatNumber().value()
        );
        dataObject.setPriceSnapshot(
                bookingOrder.amount().amount()
        );
        dataObject.setStatus(bookingOrder.status().name());
        dataObject.setExpiresAt(
                toDatabaseTime(bookingOrder.expiresAt())
        );
        dataObject.setPaymentNo(
                bookingOrder.paymentReference() == null
                        ? null
                        : bookingOrder.paymentReference().toString()
        );
        dataObject.setPaidAt(
                toNullableDatabaseTime(bookingOrder.paidAt())
        );
        dataObject.setCancelledAt(
                toNullableDatabaseTime(bookingOrder.cancelledAt())
        );
        dataObject.setCancelReason(
                bookingOrder.cancellationReason() == null
                        ? null
                        : bookingOrder.cancellationReason().name()
        );
        dataObject.setVersion(bookingOrder.version());
        dataObject.setCreatedAt(
                toDatabaseTime(bookingOrder.createdAt())
        );
        dataObject.setUpdatedAt(
                toDatabaseTime(bookingOrder.updatedAt())
        );
        return dataObject;
    }

    private BookingOrder toDomain(
            BookingOrderDataObject dataObject
    ) {
        return BookingOrder.restore(
                BookingId.of(dataObject.getId()),
                BookingNumber.of(dataObject.getOrderNo()),
                BookingRequestNumber.of(dataObject.getRequestNo()),
                UserId.of(dataObject.getUserId()),
                TripReference.of(dataObject.getTripId()),
                SeatNumber.of(dataObject.getSeatNumber()),
                new BookingAmount(dataObject.getPriceSnapshot()),
                BookingStatus.valueOf(dataObject.getStatus()),
                toInstant(dataObject.getExpiresAt()),
                dataObject.getPaymentNo() == null
                        ? null
                        : PaymentReference.of(dataObject.getPaymentNo()),
                toNullableInstant(dataObject.getPaidAt()),
                toNullableInstant(dataObject.getCancelledAt()),
                dataObject.getCancelReason() == null
                        ? null
                        : CancellationReason.valueOf(
                                dataObject.getCancelReason()
                        ),
                dataObject.getVersion(),
                toInstant(dataObject.getCreatedAt()),
                toInstant(dataObject.getUpdatedAt())
        );
    }

    private LocalDateTime toDatabaseTime(Instant instant) {
        return LocalDateTime.ofInstant(
                Objects.requireNonNull(
                        instant,
                        "instant must not be null"
                ),
                DATABASE_ZONE
        );
    }

    private LocalDateTime toNullableDatabaseTime(Instant instant) {
        return instant == null ? null : toDatabaseTime(instant);
    }

    private Instant toInstant(LocalDateTime localDateTime) {
        return localDateTime.toInstant(DATABASE_ZONE);
    }

    private Instant toNullableInstant(LocalDateTime localDateTime) {
        return localDateTime == null ? null : toInstant(localDateTime);
    }
}
