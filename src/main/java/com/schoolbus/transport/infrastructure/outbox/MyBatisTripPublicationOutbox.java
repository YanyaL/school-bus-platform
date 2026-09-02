package com.schoolbus.transport.infrastructure.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolbus.payment.infrastructure.outbox.OutboxMapper;
import com.schoolbus.shared.web.TraceContext;
import com.schoolbus.transport.application.trip.TripPublicationOutboxPort;
import com.schoolbus.transport.application.trip.TripPublishedEvent;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class MyBatisTripPublicationOutbox implements TripPublicationOutboxPort {
    private final OutboxMapper mapper;
    private final ObjectMapper objectMapper;

    public MyBatisTripPublicationOutbox(OutboxMapper mapper, ObjectMapper objectMapper) {
        this.mapper = Objects.requireNonNull(mapper);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(TripPublishedEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", TripPublishedEvent.SCHEMA_VERSION);
        // Snowflake identifiers must not become lossy JSON numbers in non-Java consumers.
        payload.put("tripId", Long.toString(event.tripId()));
        payload.put("tripNumber", event.tripNumber().toString());
        payload.put("tripVersion", event.tripVersion());
        payload.put("seatNumbers", event.seatNumbers());
        payload.put("totalSeats", event.totalSeats());
        payload.put("price", event.price().toPlainString());
        payload.put("bookingDeadline", event.bookingDeadline().toString());
        payload.put("departureTime", event.departureTime().toString());
        payload.put("publishedAt", event.publishedAt().toString());
        LocalDateTime now = LocalDateTime.ofInstant(event.publishedAt(), ZoneOffset.UTC);
        int inserted = mapper.insertEvent(UUID.randomUUID().toString(), "transport", "BusTrip",
                Long.toString(event.tripId()), event.tripVersion(), TripPublishedEvent.TYPE,
                serialize(payload), TraceContext.currentTraceId(), now, now);
        if (inserted != 1) {
            throw new IllegalStateException("failed to insert TripPublished outbox event");
        }
    }

    private String serialize(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to serialize TripPublished event", exception);
        }
    }
}
