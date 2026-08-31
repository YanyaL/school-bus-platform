package com.schoolbus.bookingservice.application.trippublication;

import java.time.Instant;

/** All methods participate in the caller's transaction. This port cannot mutate live inventory. */
public interface TripPublicationShadowStore {
    boolean insertInbox(String eventId, long tripId, String hash, Instant now);
    Inbox lockInbox(String eventId);
    boolean insertSnapshot(TripPublicationEnvelope event, String hash, String json, Instant now);
    Snapshot lockSnapshot(long tripId);
    void updateSnapshot(TripPublicationEnvelope event, String hash, String json, Instant now, long expectedVersion);
    void completeInbox(String eventId, String outcome);
    record Inbox(String payloadHash, String outcome) { }
    record Snapshot(long tripId, String tripNumber, long tripVersion, String payloadHash) { }
}
