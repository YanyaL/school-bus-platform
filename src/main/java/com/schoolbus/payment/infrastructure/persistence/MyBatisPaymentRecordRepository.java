package com.schoolbus.payment.infrastructure.persistence;

import com.schoolbus.booking.domain.order.BookingAmount;
import com.schoolbus.booking.domain.order.BookingNumber;
import com.schoolbus.payment.domain.PaymentId;
import com.schoolbus.payment.domain.PaymentNumber;
import com.schoolbus.payment.domain.PaymentRecord;
import com.schoolbus.payment.domain.PaymentRecordRepository;
import com.schoolbus.payment.domain.PaymentRequestNumber;
import com.schoolbus.payment.domain.PaymentStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;

@Repository
@Profile("!test")
public class MyBatisPaymentRecordRepository
        implements PaymentRecordRepository {

    private static final ZoneOffset DATABASE_ZONE = ZoneOffset.UTC;

    private final PaymentRecordMapper mapper;

    public MyBatisPaymentRecordRepository(PaymentRecordMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    @Override
    public PaymentRecord save(PaymentRecord paymentRecord) {
        PaymentRecord validated = Objects.requireNonNull(
                paymentRecord,
                "paymentRecord must not be null"
        );
        if (validated.version() != 0L) {
            throw new IllegalArgumentException("only new payment records can be saved");
        }
        if (mapper.insertPayment(toDataObject(validated)) != 1) {
            throw new IllegalStateException("failed to insert payment record");
        }
        return validated;
    }

    @Override
    public Optional<PaymentRecord> findByRequestNumber(
            PaymentRequestNumber requestNumber
    ) {
        PaymentRequestNumber validated = Objects.requireNonNull(
                requestNumber,
                "requestNumber must not be null"
        );
        return toOptional(mapper.selectByRequestNo(validated.value()));
    }

    @Override
    public Optional<PaymentRecord> findByPaymentNumber(
            PaymentNumber paymentNumber
    ) {
        PaymentNumber validated = Objects.requireNonNull(
                paymentNumber,
                "paymentNumber must not be null"
        );
        return toOptional(mapper.selectByPaymentNo(validated.toString()));
    }

    private Optional<PaymentRecord> toOptional(PaymentRecordDataObject dataObject) {
        return dataObject == null ? Optional.empty() : Optional.of(toDomain(dataObject));
    }

    private PaymentRecordDataObject toDataObject(PaymentRecord record) {
        PaymentRecordDataObject dataObject = new PaymentRecordDataObject();
        dataObject.setId(record.paymentId().value());
        dataObject.setPaymentNo(record.paymentNumber().toString());
        dataObject.setRequestNo(record.requestNumber().value());
        dataObject.setOrderNo(record.bookingNumber().toString());
        dataObject.setAmount(record.amount().amount());
        dataObject.setStatus(record.status().name());
        dataObject.setFailureReason(record.failureReason());
        dataObject.setCompletedAt(toDatabaseTime(record.completedAt()));
        dataObject.setVersion(record.version());
        dataObject.setCreatedAt(toDatabaseTime(record.createdAt()));
        dataObject.setUpdatedAt(toDatabaseTime(record.updatedAt()));
        return dataObject;
    }

    private PaymentRecord toDomain(PaymentRecordDataObject dataObject) {
        return PaymentRecord.restore(
                PaymentId.of(dataObject.getId()),
                PaymentNumber.of(dataObject.getPaymentNo()),
                PaymentRequestNumber.of(dataObject.getRequestNo()),
                BookingNumber.of(dataObject.getOrderNo()),
                new BookingAmount(dataObject.getAmount()),
                PaymentStatus.valueOf(dataObject.getStatus()),
                dataObject.getFailureReason(),
                toInstant(dataObject.getCompletedAt()),
                dataObject.getVersion(),
                toInstant(dataObject.getCreatedAt()),
                toInstant(dataObject.getUpdatedAt())
        );
    }

    private LocalDateTime toDatabaseTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, DATABASE_ZONE);
    }

    private Instant toInstant(LocalDateTime localDateTime) {
        return localDateTime.toInstant(DATABASE_ZONE);
    }
}
