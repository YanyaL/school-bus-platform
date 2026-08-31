package com.schoolbus.bookingservice.infrastructure.persistence.trippublication;

import com.schoolbus.bookingservice.application.trippublication.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Transactional(propagation = Propagation.MANDATORY)
public class MyBatisTripPublicationShadowStore implements TripPublicationShadowStore {
    private final TripPublicationShadowMapper mapper;
    public MyBatisTripPublicationShadowStore(TripPublicationShadowMapper mapper) { this.mapper = mapper; }

    @Override
    public boolean insertInbox(String eventId, long tripId, String hash, Instant now) {
        try { ensureOne(mapper.insertInbox(eventId, tripId, hash, time(now))); return true; }
        catch (DuplicateKeyException duplicate) { return false; }
    }
    @Override
    public Inbox lockInbox(String eventId) { return mapper.lockInbox(eventId); }

    @Override
    public boolean insertSnapshot(TripPublicationEnvelope event, String hash, String json, Instant now) {
        var snapshot = event.snapshot();
        try {
            ensureOne(mapper.insertSnapshot(snapshot.tripId(), snapshot.tripNumber().toString(), snapshot.tripVersion(),
                    hash, json, event.eventId(), time(now)));
            return true;
        } catch (DuplicateKeyException duplicate) { return false; }
    }
    @Override
    public Snapshot lockSnapshot(long tripId) { return mapper.lockSnapshot(tripId); }

    @Override
    public void updateSnapshot(TripPublicationEnvelope event, String hash, String json, Instant now, long expectedVersion) {
        ensureOne(mapper.updateSnapshot(event.snapshot().tripId(), event.snapshot().tripVersion(), hash, json,
                event.eventId(), time(now), expectedVersion));
    }
    @Override
    public void completeInbox(String eventId, String outcome) { ensureOne(mapper.completeInbox(eventId, outcome)); }

    private static LocalDateTime time(Instant now) { return LocalDateTime.ofInstant(now, ZoneOffset.UTC); }
    private static void ensureOne(int count) {
        if (count != 1) throw new OptimisticLockingFailureException("shadow observation write lost");
    }
}
