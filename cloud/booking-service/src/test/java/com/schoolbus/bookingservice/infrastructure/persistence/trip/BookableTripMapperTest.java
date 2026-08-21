package com.schoolbus.bookingservice.infrastructure.persistence.trip;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class BookableTripMapperTest {

    @Test
    void locksTheTripRowWhileCreatingABooking() throws Exception {
        Method query = BookableTripMapper.class.getMethod(
                "selectByTripNumber",
                String.class
        );
        Select select = query.getAnnotation(Select.class);

        assertThat(select).isNotNull();
        assertThat(String.join(" ", select.value()))
                .as("the booking transaction must preserve the Core FOR SHARE lock")
                .containsIgnoringCase("FOR SHARE");
    }
}
