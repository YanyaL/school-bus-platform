package com.schoolbus.transportquery.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.schoolbus.transportquery.api.ApiErrorResponse;
import com.schoolbus.transportquery.api.ApiResponse;
import com.schoolbus.transportquery.api.BookableTripResponse;
import com.schoolbus.transportquery.api.ErrorCode;
import com.schoolbus.transportquery.api.TripSeatMapResponse;
import com.schoolbus.transportquery.api.TripSeatResponse;
import com.schoolbus.transportquery.application.BookableTripView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden JSON contract mirroring school-bus-core student trip responses.
 */
class StudentTripHttpContractTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Test
    void bookableTripListContract() throws Exception {
        BookableTripView view = new BookableTripView(
                9_007_199_254_740_991L,
                "11111111-1111-1111-1111-111111111111",
                3001L,
                2001L,
                Instant.parse("2026-08-05T09:00:00Z"),
                Instant.parse("2026-08-05T08:00:00Z"),
                new BigDecimal("5.00")
        );
        ApiResponse<List<BookableTripResponse>> response =
                ApiResponse.success(List.of(BookableTripResponse.from(view)));

        JsonNode root = objectMapper.readTree(objectMapper.writeValueAsString(response));
        assertThat(root.get("code").asText()).isEqualTo("OK");
        assertThat(root.get("message").asText()).isEqualTo("success");
        JsonNode item = root.get("data").get(0);
        assertThat(item.has("tripId")).isFalse();
        assertThat(item.get("tripNumber").asText())
                .isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(item.get("vehicleId").asText()).isEqualTo("3001");
        assertThat(item.get("routeId").asText()).isEqualTo("2001");
        assertThat(item.get("price").decimalValue()).isEqualByComparingTo("5.00");
    }

    @Test
    void emptyBookableTripListContract() throws Exception {
        ApiResponse<List<BookableTripResponse>> response = ApiResponse.success(List.of());
        JsonNode root = objectMapper.readTree(objectMapper.writeValueAsString(response));
        assertThat(root.get("data").isArray()).isTrue();
        assertThat(root.get("data")).isEmpty();
    }

    @Test
    void seatMapContract() throws Exception {
        TripSeatMapResponse seatMap = new TripSeatMapResponse(
                "11111111-1111-1111-1111-111111111111",
                Instant.parse("2026-08-05T08:00:00Z"),
                List.of(
                        new TripSeatResponse("A01", "AVAILABLE"),
                        new TripSeatResponse("A02", "LOCKED")
                )
        );
        ApiResponse<TripSeatMapResponse> response = ApiResponse.success(seatMap);
        JsonNode root = objectMapper.readTree(objectMapper.writeValueAsString(response));
        JsonNode data = root.get("data");
        assertThat(data.has("tripId")).isFalse();
        assertThat(data.get("tripNumber").asText())
                .isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(data.get("seats").get(0).get("status").asText()).isEqualTo("AVAILABLE");
    }

    @Test
    void tripNotFoundContract() throws Exception {
        ApiErrorResponse error = ApiErrorResponse.of(
                new com.schoolbus.transportquery.api.BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "trip not found: 11111111-1111-1111-1111-111111111111"
                )
        );
        JsonNode root = objectMapper.readTree(objectMapper.writeValueAsString(error));
        assertThat(root.get("code").asText()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(root.get("message").asText()).contains("trip not found");
    }

    @Test
    void invalidTripNumberContract() throws Exception {
        ApiErrorResponse error = ApiErrorResponse.of(
                new com.schoolbus.transportquery.api.BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "tripNumber must be a valid UUID"
                )
        );
        JsonNode root = objectMapper.readTree(objectMapper.writeValueAsString(error));
        assertThat(root.get("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(root.get("message").asText())
                .isEqualTo("tripNumber must be a valid UUID");
    }
}
