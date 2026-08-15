package com.schoolbus.shared.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpResourceIdTest {

    private static final long SNOWFLAKE_ID = 81_765_424_194_125_824L;

    @Test
    void shouldFormatSnowflakeIdAsDecimalString() {
        assertThat(HttpResourceId.format(SNOWFLAKE_ID))
                .isEqualTo("81765424194125824");
    }

    @Test
    void shouldParseSnowflakeId() {
        assertThat(HttpResourceId.parse("81765424194125824", "tripId"))
                .isEqualTo(SNOWFLAKE_ID);
    }

    @Test
    void shouldRejectNull() {
        assertThatThrownBy(() -> HttpResourceId.parse(null, "tripId"))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException business = (BusinessException) exception;
                    assertThat(business.errorCode())
                            .isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(business.getMessage())
                            .contains("tripId");
                });
    }

    @Test
    void shouldRejectBlank() {
        assertThatThrownBy(() -> HttpResourceId.parse("   ", "tripId"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void shouldRejectZero() {
        assertThatThrownBy(() -> HttpResourceId.parse("0", "tripId"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void shouldRejectNegativeSign() {
        assertThatThrownBy(() -> HttpResourceId.parse("-1", "tripId"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void shouldRejectLetters() {
        assertThatThrownBy(() -> HttpResourceId.parse("12a3", "tripId"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void shouldRejectDecimal() {
        assertThatThrownBy(() -> HttpResourceId.parse("12.3", "tripId"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void shouldRejectScientificNotation() {
        assertThatThrownBy(() -> HttpResourceId.parse("1e10", "tripId"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void shouldRejectValueExceedingLongMax() {
        assertThatThrownBy(() ->
                HttpResourceId.parse("9223372036854775808", "tripId")
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException business = (BusinessException) exception;
                    assertThat(business.errorCode())
                            .isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(business.getMessage())
                            .contains("Long.MAX_VALUE");
                });
    }

    @Test
    void shouldRejectLeadingPlusSign() {
        assertThatThrownBy(() -> HttpResourceId.parse("+12", "tripId"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void shouldRejectNonPositiveFormatInput() {
        assertThatThrownBy(() -> HttpResourceId.format(0L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
