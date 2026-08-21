package com.schoolbus.bookingservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;

import static org.assertj.core.api.Assertions.assertThat;

class BookingOwnershipInfoContributorTest {

    @Test
    void reportsBookingOwnership() {
        Info.Builder builder = new Info.Builder();
        new BookingOwnershipInfoContributor().contribute(builder);
        Info info = builder.build();
        assertThat(info.getDetails())
                .containsEntry(
                        BookingOwnershipInfoContributor.DETAIL_KEY,
                        "booking"
                );
    }
}
