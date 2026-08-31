package com.schoolbus.bookingservice.application.trippublication;

import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import static com.schoolbus.bookingservice.trippublication.PublicationFixtures.*;
import static com.schoolbus.bookingservice.application.trippublication.TripPublicationShadowTransaction.Outcome.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TripPublicationShadowTransactionTest {
    private final TripPublicationShadowStore store = mock(TripPublicationShadowStore.class);
    private final TripPublicationShadowTransaction service = new TripPublicationShadowTransaction(store,
            Clock.fixed(Instant.parse("2026-08-31T01:00:00Z"), ZoneOffset.UTC));

    @Test
    void insertsInboxThenSnapshotAndCompletesMarker() {
        fresh(); when(store.insertSnapshot(any(), anyString(), anyString(), any())).thenReturn(true);
        assertThat(service.observe(event())).isEqualTo(APPLIED);
        var order = inOrder(store);
        order.verify(store).insertInbox(eq(EVENT_ID), eq(9007199254740993L), anyString(), any());
        order.verify(store).insertSnapshot(any(), anyString(), anyString(), any());
        order.verify(store).completeInbox(EVENT_ID, "APPLIED");
    }
    @Test
    void identicalEventReturnsDuplicateWithoutUpdatingProjectionOrTimestamp() throws Exception {
        when(store.lockInbox(EVENT_ID)).thenReturn(new TripPublicationShadowStore.Inbox(hash(event()), "APPLIED"));
        assertThat(service.observe(event())).isEqualTo(DUPLICATE);
        verify(store, never()).insertSnapshot(any(), anyString(), anyString(), any());
        verify(store, never()).completeInbox(anyString(), anyString());
    }
    @Test
    void sameEventIdDifferentContentIsPermanentConflict() {
        when(store.lockInbox(EVENT_ID)).thenReturn(new TripPublicationShadowStore.Inbox("different", "APPLIED"));
        assertThatThrownBy(() -> service.observe(event())).isInstanceOf(TripPublicationRejectedException.class);
    }
    @Test
    void incompleteCommittedMarkerIsNotMistakenForSuccess() throws Exception {
        when(store.lockInbox(EVENT_ID)).thenReturn(new TripPublicationShadowStore.Inbox(hash(event()), "PROCESSING"));
        assertThatThrownBy(() -> service.observe(event())).isInstanceOf(TripPublicationRejectedException.class);
    }
    @Test
    void staleVersionIsRecordedButNeverOverwritesNewerSnapshot() {
        fresh(); current(3, "newer");
        assertThat(service.observe(event())).isEqualTo(STALE);
        verify(store, never()).updateSnapshot(any(), anyString(), anyString(), any(), anyLong());
        verify(store).completeInbox(EVENT_ID, "STALE");
    }
    @Test
    void newEventIdSameVersionAndContentsIsAlreadyApplied() throws Exception {
        fresh(); current(1, hash(event()));
        assertThat(service.observe(event())).isEqualTo(ALREADY_APPLIED);
        verify(store, never()).updateSnapshot(any(), anyString(), anyString(), any(), anyLong());
    }
    @Test
    void sameCurrentVersionDifferentContentsIsRejected() {
        fresh(); current(1, "different");
        assertThatThrownBy(() -> service.observe(event())).isInstanceOf(TripPublicationRejectedException.class);
        verify(store, never()).completeInbox(anyString(), anyString());
    }
    @Test
    void newerVersionUpdatesWithExpectedVersion() {
        fresh(); current(1, "older");
        var newer = version(2);
        assertThat(service.observe(newer)).isEqualTo(APPLIED);
        verify(store).updateSnapshot(eq(newer), anyString(), anyString(), any(), eq(1L));
    }
    @Test
    void tripIdentityCannotBeReassignedEvenByNewerVersion() {
        fresh();
        when(store.lockSnapshot(anyLong())).thenReturn(new TripPublicationShadowStore.Snapshot(9007199254740993L,
                "99999999-9999-4999-8999-999999999999", 1, "older"));
        assertThatThrownBy(() -> service.observe(version(2))).isInstanceOf(TripPublicationRejectedException.class);
    }
    @Test
    void completionFailurePropagatesToTransactionProxy() {
        fresh(); when(store.insertSnapshot(any(), anyString(), anyString(), any())).thenReturn(true);
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("offline"))
                .when(store).completeInbox(anyString(), anyString());
        assertThatThrownBy(() -> service.observe(event())).isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);
    }
    private void fresh() { when(store.insertInbox(anyString(), anyLong(), anyString(), any())).thenReturn(true); }
    private void current(long version, String hash) {
        when(store.lockSnapshot(anyLong())).thenReturn(new TripPublicationShadowStore.Snapshot(9007199254740993L,
                event().snapshot().tripNumber().toString(), version, hash));
    }
    private static String hash(TripPublicationEnvelope event) throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(event.snapshot().canonicalJson().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
