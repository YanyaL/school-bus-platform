package com.schoolbus.booking.infrastructure.identity;

import com.schoolbus.booking.domain.order.BookingId;
import com.schoolbus.booking.domain.order.BookingNumber;
import com.schoolbus.shared.domain.identity.UserId;
import com.schoolbus.shared.infrastructure.identity.SnowflakeIdGenerator;
import com.schoolbus.shared.infrastructure.identity.SnowflakeUserIdGenerator;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BookingIdentityGeneratorTest {

    @Test
    void shouldGenerateUniqueIdsAcrossUserAndBookingAdapters() {
        SnowflakeIdGenerator sharedGenerator =
                new SnowflakeIdGenerator(1L);
        SnowflakeUserIdGenerator userIdGenerator =
                new SnowflakeUserIdGenerator(sharedGenerator);
        SnowflakeBookingIdGenerator bookingIdGenerator =
                new SnowflakeBookingIdGenerator(sharedGenerator);

        Set<Long> generatedIds = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            UserId userId = userIdGenerator.nextId();
            BookingId bookingId = bookingIdGenerator.nextId();
            generatedIds.add(userId.value());
            generatedIds.add(bookingId.value());
        }

        assertThat(generatedIds)
                .hasSize(2000)
                .allMatch(id -> id > 0);
    }

    @Test
    void shouldGenerateUuidBookingNumbers() {
        UuidBookingNumberGenerator generator =
                new UuidBookingNumberGenerator();

        BookingNumber first = generator.nextNumber();
        BookingNumber second = generator.nextNumber();

        assertThat(first).isNotEqualTo(second);
        assertThat(first.toString()).hasSize(36);
        assertThat(second.toString()).hasSize(36);
    }
}
