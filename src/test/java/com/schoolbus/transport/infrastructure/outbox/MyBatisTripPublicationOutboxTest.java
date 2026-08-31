package com.schoolbus.transport.infrastructure.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolbus.payment.infrastructure.outbox.OutboxMapper;
import com.schoolbus.transport.application.trip.TripPublishedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MyBatisTripPublicationOutboxTest {
    private final OutboxMapper mapper = mock(OutboxMapper.class);
    private final ObjectMapper json = new ObjectMapper();
    private final MyBatisTripPublicationOutbox outbox = new MyBatisTripPublicationOutbox(mapper, json);

    @Test
    void writesVersionedWireContractWithoutSnowflakePrecisionLoss() throws Exception {
        when(mapper.insertEvent(anyString(), eq("transport"), eq("BusTrip"), anyString(), eq(1L),
                eq("TripPublished"), anyString(), nullable(String.class), any(), any())).thenReturn(1);
        outbox.append(event());
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> eventId = ArgumentCaptor.forClass(String.class);
        verify(mapper).insertEvent(eventId.capture(), eq("transport"), eq("BusTrip"),
                eq("9007199254740993"), eq(1L), eq("TripPublished"), payload.capture(), nullable(String.class),
                eq(LocalDateTime.parse("2026-08-31T00:00:00")), eq(LocalDateTime.parse("2026-08-31T00:00:00")));
        assertThat(UUID.fromString(eventId.getValue())).isNotNull();
        JsonNode body = json.readTree(payload.getValue());
        assertThat(body.get("tripId").isTextual()).isTrue();
        assertThat(body.get("tripId").asText()).isEqualTo("9007199254740993");
        assertThat(body.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(body.get("tripVersion").asLong()).isEqualTo(1);
        assertThat(body.get("seatNumbers").size()).isEqualTo(body.get("totalSeats").asInt());
        assertThat(body.get("price").asText()).isEqualTo("5.00");
        assertThat(body.get("publishedAt").asText()).isEqualTo("2026-08-31T00:00:00Z");
        assertThat(body).isEqualTo(json.readTree(java.nio.file.Path.of("contracts/trip-published-v1.json").toFile()));
    }

    @Test
    void refusesSilentInsertFailure() {
        assertThatThrownBy(() -> outbox.append(event())).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("failed to insert");
    }

    private TripPublishedEvent event() {
        Instant now = Instant.parse("2026-08-31T00:00:00Z");
        return new TripPublishedEvent(9007199254740993L, UUID.fromString("11111111-1111-4111-8111-111111111111"), 1, List.of("1", "2"),
                new BigDecimal("5.00"), now.plusSeconds(60), now.plusSeconds(120), now);
    }
}
