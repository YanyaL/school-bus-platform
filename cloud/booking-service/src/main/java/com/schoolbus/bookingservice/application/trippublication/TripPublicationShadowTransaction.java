package com.schoolbus.bookingservice.application.trippublication;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

public class TripPublicationShadowTransaction {
    public enum Outcome { APPLIED, DUPLICATE, STALE, ALREADY_APPLIED }
    private final TripPublicationShadowStore store;
    private final Clock clock;

    public TripPublicationShadowTransaction(TripPublicationShadowStore store, Clock clock) {
        this.store = Objects.requireNonNull(store);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Outcome observe(TripPublicationEnvelope event) {
        Objects.requireNonNull(event);
        var snapshot = event.snapshot();
        String json = snapshot.canonicalJson();
        String hash = sha256(json);
        Instant now = clock.instant();
        if (!store.insertInbox(event.eventId(), snapshot.tripId(), hash, now)) {
            var inbox = store.lockInbox(event.eventId());
            if (inbox == null || !hash.equals(inbox.payloadHash()) || "PROCESSING".equals(inbox.outcome())) {
                throw new TripPublicationRejectedException("event identity conflicts with stored observation");
            }
            return Outcome.DUPLICATE;
        }

        Outcome outcome;
        if (store.insertSnapshot(event, hash, json, now)) {
            outcome = Outcome.APPLIED;
        } else {
            var current = store.lockSnapshot(snapshot.tripId());
            if (current == null || !snapshot.tripNumber().toString().equals(current.tripNumber())) {
                throw new TripPublicationRejectedException("trip identity conflicts with shadow snapshot");
            }
            if (snapshot.tripVersion() < current.tripVersion()) {
                outcome = Outcome.STALE;
            } else if (snapshot.tripVersion() == current.tripVersion()) {
                if (!hash.equals(current.payloadHash())) {
                    throw new TripPublicationRejectedException("same trip version has a different snapshot");
                }
                outcome = Outcome.ALREADY_APPLIED;
            } else {
                store.updateSnapshot(event, hash, json, now, current.tripVersion());
                outcome = Outcome.APPLIED;
            }
        }
        store.completeInbox(event.eventId(), outcome.name());
        return outcome;
    }

    private static String sha256(String json) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(json.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
