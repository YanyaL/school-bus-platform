package com.schoolbus.bookingservice.application.trippublication;

import java.util.Objects;
import java.util.UUID;

public record TripPublicationEnvelope(String eventId, TripPublicationSnapshot snapshot) {
    public TripPublicationEnvelope {
        Objects.requireNonNull(snapshot);
        UUID id = UUID.fromString(Objects.requireNonNull(eventId));
        if (!id.toString().equalsIgnoreCase(eventId)) throw new IllegalArgumentException("canonical event UUID required");
        eventId = id.toString();
    }
}
