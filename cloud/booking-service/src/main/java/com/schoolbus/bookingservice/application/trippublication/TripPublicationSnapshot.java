package com.schoolbus.bookingservice.application.trippublication;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

/** Publication-time facts, NOT live availability, cancellation status or an inventory initialization command. */
public record TripPublicationSnapshot(long tripId, UUID tripNumber, long tripVersion,
        List<String> seatNumbers, BigDecimal price, Instant bookingDeadline, Instant departureTime, Instant publishedAt) {
    // Isolate fingerprints from application-level pretty printing/custom serializers on HTTP ObjectMapper beans.
    private static final com.fasterxml.jackson.databind.ObjectWriter CANONICAL_JSON = new ObjectMapper().writer();
    public TripPublicationSnapshot {
        if (tripId <= 0 || tripVersion <= 0) throw new IllegalArgumentException("positive trip identity/version required");
        Objects.requireNonNull(tripNumber);
        seatNumbers = List.copyOf(seatNumbers);
        if (seatNumbers.isEmpty() || seatNumbers.size() > 1000 || new HashSet<>(seatNumbers).size() != seatNumbers.size()
                || seatNumbers.stream().anyMatch(s -> s.isBlank() || s.length() > 10)) {
            throw new IllegalArgumentException("invalid seat snapshot");
        }
        price = Objects.requireNonNull(price).setScale(2, RoundingMode.UNNECESSARY);
        if (price.signum() < 0 || price.compareTo(new BigDecimal("99999999.99")) > 0) {
            throw new IllegalArgumentException("invalid trip price");
        }
        Objects.requireNonNull(publishedAt);
        Objects.requireNonNull(bookingDeadline);
        Objects.requireNonNull(departureTime);
        if (!publishedAt.isBefore(bookingDeadline) || !bookingDeadline.isBefore(departureTime)) {
            throw new IllegalArgumentException("invalid publication timeline");
        }
    }

    public String canonicalJson() {
        Map<String, Object> fields = new TreeMap<>();
        fields.put("schemaVersion", 1);
        fields.put("tripId", Long.toString(tripId));
        fields.put("tripNumber", tripNumber.toString());
        fields.put("tripVersion", tripVersion);
        fields.put("seatNumbers", seatNumbers);
        fields.put("totalSeats", seatNumbers.size());
        fields.put("price", price.toPlainString());
        fields.put("bookingDeadline", bookingDeadline.toString());
        fields.put("departureTime", departureTime.toString());
        fields.put("publishedAt", publishedAt.toString());
        try { return CANONICAL_JSON.writeValueAsString(fields); }
        catch (JsonProcessingException e) { throw new IllegalStateException("cannot serialize shadow snapshot", e); }
    }
}
