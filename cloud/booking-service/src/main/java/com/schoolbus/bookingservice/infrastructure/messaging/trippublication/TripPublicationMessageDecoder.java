package com.schoolbus.bookingservice.infrastructure.messaging.trippublication;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.schoolbus.bookingservice.application.trippublication.*;
import org.springframework.amqp.core.Message;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public class TripPublicationMessageDecoder {
    private static final Set<String> FIELDS = Set.of("schemaVersion", "tripId", "tripNumber", "tripVersion",
            "seatNumbers", "totalSeats", "price", "bookingDeadline", "departureTime", "publishedAt");
    private final ObjectReader reader;

    public TripPublicationMessageDecoder(ObjectMapper mapper) {
        reader = mapper.reader().with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    public TripPublicationEnvelope decode(Message message) {
        try {
            if (message.getBody().length > 65536) throw new IllegalArgumentException("message too large");
            var properties = message.getMessageProperties();
            if (!"TripPublished".equals(properties.getType())) throw new IllegalArgumentException("wrong event type");
            String eventId = properties.getMessageId();
            Object headerId = properties.getHeader("eventId");
            if (headerId != null && !headerId.equals(eventId)) throw new IllegalArgumentException("conflicting event IDs");
            Object headerVersion = properties.getHeader("schemaVersion");
            if (headerVersion != null && !Integer.valueOf(1).equals(headerVersion)) {
                throw new IllegalArgumentException("unsupported header schema version");
            }
            JsonNode json = reader.readTree(message.getBody());
            if (json == null || !json.isObject() || json.size() != FIELDS.size()) throw new IllegalArgumentException("invalid fields");
            json.fieldNames().forEachRemaining(name -> {
                if (!FIELDS.contains(name)) throw new IllegalArgumentException("unknown schema-v1 field");
            });
            if (integer(json, "schemaVersion") != 1) throw new IllegalArgumentException("unsupported schema version");
            String tripId = text(json, "tripId");
            if (!tripId.matches("[1-9][0-9]{0,18}")) throw new IllegalArgumentException("tripId must be a positive JSON string");
            String tripNumber = text(json, "tripNumber");
            UUID uuid = UUID.fromString(tripNumber);
            if (!uuid.toString().equalsIgnoreCase(tripNumber)) throw new IllegalArgumentException("canonical trip UUID required");
            JsonNode seats = json.get("seatNumbers");
            if (!seats.isArray() || seats.size() != integer(json, "totalSeats")) throw new IllegalArgumentException("seat count mismatch");
            List<String> numbers = new ArrayList<>();
            for (JsonNode seat : seats) {
                if (!seat.isTextual()) throw new IllegalArgumentException("seat number must be textual");
                numbers.add(seat.textValue());
            }
            String price = text(json, "price");
            if (!price.matches("[0-9]{1,8}\\.[0-9]{2}")) throw new IllegalArgumentException("decimal price string required");
            return new TripPublicationEnvelope(eventId, new TripPublicationSnapshot(Long.parseLong(tripId), uuid,
                    integer(json, "tripVersion"), numbers, new BigDecimal(price), Instant.parse(text(json, "bookingDeadline")),
                    Instant.parse(text(json, "departureTime")), Instant.parse(text(json, "publishedAt"))));
        } catch (Exception invalid) {
            throw new TripPublicationRejectedException("invalid TripPublished v1 message", invalid);
        }
    }

    private static String text(JsonNode json, String name) {
        JsonNode value = json.get(name);
        if (value == null || !value.isTextual()) throw new IllegalArgumentException(name + " must be textual");
        return value.textValue();
    }
    private static long integer(JsonNode json, String name) {
        JsonNode value = json.get(name);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
            throw new IllegalArgumentException(name + " must be an integer");
        }
        return value.longValue();
    }
}
