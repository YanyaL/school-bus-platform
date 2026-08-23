package com.schoolbus.paymentservice.architecture;

import com.schoolbus.paymentservice.infrastructure.booking.OutboxRefundedBookingAdapter;
import com.schoolbus.paymentservice.infrastructure.booking.SharedDatabaseRefundedBookingAdapter;
import com.schoolbus.paymentservice.infrastructure.persistence.RefundBookingMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentEventModeRefundAdapterArchitectureTest {

    @Test
    void sharedDatabaseAdapterIsConditionalOnDirectOnly() {
        ConditionalOnProperty condition =
                SharedDatabaseRefundedBookingAdapter.class.getAnnotation(
                        ConditionalOnProperty.class
                );
        assertThat(condition).isNotNull();
        assertThat(condition.havingValue()).isEqualTo("DIRECT");
        assertThat(condition.prefix())
                .isEqualTo("school-bus.payment.migration");
        assertThat(condition.name())
                .containsExactly("booking-write-mode");
    }

    @Test
    void outboxAdapterIsConditionalOnEventWithMatchIfMissing() {
        ConditionalOnProperty condition =
                OutboxRefundedBookingAdapter.class.getAnnotation(
                        ConditionalOnProperty.class
                );
        assertThat(condition).isNotNull();
        assertThat(condition.havingValue()).isEqualTo("EVENT");
        assertThat(condition.matchIfMissing()).isTrue();
    }

    @Test
    void outboxAdapterDoesNotReferenceRefundBookingMapper() {
        boolean referencesMapper = Arrays.stream(
                OutboxRefundedBookingAdapter.class.getDeclaredFields()
        ).map(Field::getType).anyMatch(
                type -> type == RefundBookingMapper.class
        );
        assertThat(referencesMapper).isFalse();
    }
}
