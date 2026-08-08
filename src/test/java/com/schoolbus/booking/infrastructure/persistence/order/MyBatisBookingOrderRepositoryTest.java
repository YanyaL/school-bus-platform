package com.schoolbus.booking.infrastructure.persistence.order;

import com.schoolbus.booking.domain.order.BookingAmount;
import com.schoolbus.booking.domain.order.BookingId;
import com.schoolbus.booking.domain.order.BookingNumber;
import com.schoolbus.booking.domain.order.BookingOrder;
import com.schoolbus.booking.domain.order.BookingRequestNumber;
import com.schoolbus.booking.domain.order.BookingStatus;
import com.schoolbus.booking.domain.order.SeatNumber;
import com.schoolbus.booking.domain.trip.TripReference;
import com.schoolbus.shared.domain.identity.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyBatisBookingOrderRepositoryTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-08-08T00:00:00Z");
    private static final Instant EXPIRES_AT =
            Instant.parse("2026-08-08T00:15:00Z");

    @Mock
    private BookingOrderMapper mapper;

    private MyBatisBookingOrderRepository repository;

    @BeforeEach
    void setUp() {
        repository = new MyBatisBookingOrderRepository(mapper);
    }

    @Test
    void shouldInsertNewOrder() {
        BookingOrder order = pendingOrder();
        when(mapper.insertOrder(any())).thenReturn(1);

        repository.save(order);

        ArgumentCaptor<BookingOrderDataObject> captor =
                ArgumentCaptor.forClass(
                        BookingOrderDataObject.class
                );
        verify(mapper).insertOrder(captor.capture());
        BookingOrderDataObject inserted = captor.getValue();
        assertThat(inserted.getId()).isEqualTo(5001L);
        assertThat(inserted.getOrderNo()).isEqualTo(
                "55555555-5555-5555-5555-555555555555"
        );
        assertThat(inserted.getRequestNo())
                .isEqualTo("request-5001");
        assertThat(inserted.getSeatNumber()).isEqualTo("A01");
        assertThat(inserted.getStatus())
                .isEqualTo("PENDING_PAYMENT");
        assertThat(inserted.getVersion()).isZero();
    }

    @Test
    void shouldUpdateCancelledOrderUsingPreviousVersion() {
        BookingOrder order = pendingOrder();
        order.cancel(CREATED_AT.plusSeconds(60));
        when(mapper.updateWithVersion(any(), any()))
                .thenReturn(1);

        repository.save(order);

        ArgumentCaptor<BookingOrderDataObject> orderCaptor =
                ArgumentCaptor.forClass(
                        BookingOrderDataObject.class
                );
        ArgumentCaptor<Long> versionCaptor =
                ArgumentCaptor.forClass(Long.class);
        verify(mapper).updateWithVersion(
                orderCaptor.capture(),
                versionCaptor.capture()
        );
        assertThat(orderCaptor.getValue().getStatus())
                .isEqualTo("CANCELLED");
        assertThat(orderCaptor.getValue().getCancelReason())
                .isEqualTo("USER_CANCELLED");
        assertThat(orderCaptor.getValue().getVersion())
                .isEqualTo(1L);
        assertThat(versionCaptor.getValue()).isZero();
    }

    @Test
    void shouldReportOptimisticLockConflict() {
        BookingOrder order = pendingOrder();
        order.cancel(CREATED_AT.plusSeconds(60));
        when(mapper.updateWithVersion(any(), any()))
                .thenReturn(0);

        assertThatThrownBy(() -> repository.save(order))
                .isInstanceOf(
                        OptimisticLockingFailureException.class
                )
                .hasMessage(
                        "booking order was modified by another request"
                );
    }

    @Test
    void shouldRestoreOrderFromDataObject() {
        BookingOrderDataObject dataObject = dataObject();
        when(mapper.selectById(5001L)).thenReturn(dataObject);

        Optional<BookingOrder> result = repository.findById(
                BookingId.of(5001L)
        );

        assertThat(result).isPresent();
        BookingOrder restored = result.orElseThrow();
        assertThat(restored.bookingNumber().toString())
                .isEqualTo(dataObject.getOrderNo());
        assertThat(restored.amount())
                .isEqualTo(BookingAmount.of("5.50"));
        assertThat(restored.status())
                .isEqualTo(BookingStatus.PENDING_PAYMENT);
    }

    @Test
    void shouldCheckOnlyActiveDuplicateBooking() {
        when(mapper.existsActiveByUserIdAndTripId(1001L, 2001L))
                .thenReturn(true);

        boolean exists = repository
                .existsActiveByUserIdAndTripReference(
                        UserId.of(1001L),
                        TripReference.of(2001L)
                );

        assertThat(exists).isTrue();
    }

    private BookingOrder pendingOrder() {
        return BookingOrder.place(
                BookingId.of(5001L),
                BookingNumber.of(
                        "55555555-5555-5555-5555-555555555555"
                ),
                BookingRequestNumber.of("request-5001"),
                UserId.of(1001L),
                TripReference.of(2001L),
                SeatNumber.of("A01"),
                BookingAmount.of("5.50"),
                EXPIRES_AT,
                CREATED_AT
        );
    }

    private BookingOrderDataObject dataObject() {
        BookingOrderDataObject dataObject =
                new BookingOrderDataObject();
        dataObject.setId(5001L);
        dataObject.setOrderNo(
                "55555555-5555-5555-5555-555555555555"
        );
        dataObject.setRequestNo("request-5001");
        dataObject.setUserId(1001L);
        dataObject.setTripId(2001L);
        dataObject.setSeatNumber("A01");
        dataObject.setPriceSnapshot(new BigDecimal("5.50"));
        dataObject.setStatus("PENDING_PAYMENT");
        dataObject.setExpiresAt(toDatabaseTime(EXPIRES_AT));
        dataObject.setVersion(0L);
        dataObject.setCreatedAt(toDatabaseTime(CREATED_AT));
        dataObject.setUpdatedAt(toDatabaseTime(CREATED_AT));
        return dataObject;
    }

    private LocalDateTime toDatabaseTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
