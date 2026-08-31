package com.schoolbus.bookingservice.infrastructure.persistence.trippublication;

import org.junit.jupiter.api.Test;
import org.springframework.dao.*;
import java.time.Instant;
import static com.schoolbus.bookingservice.trippublication.PublicationFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MyBatisTripPublicationShadowStoreTest {
    private final TripPublicationShadowMapper mapper = mock(TripPublicationShadowMapper.class);
    private final MyBatisTripPublicationShadowStore store = new MyBatisTripPublicationShadowStore(mapper);
    private final Instant now = Instant.parse("2026-08-31T01:00:00Z");

    @Test
    void onlyDuplicateKeyIsTranslatedToAlreadyPresent() {
        when(mapper.insertInbox(any(), anyLong(), any(), any())).thenThrow(new DuplicateKeyException("duplicate"));
        assertThat(store.insertInbox(EVENT_ID, 1, "hash", now)).isFalse();
        doThrow(new DataAccessResourceFailureException("offline")).when(mapper).insertInbox(any(), anyLong(), any(), any());
        assertThatThrownBy(() -> store.insertInbox(EVENT_ID, 1, "hash", now)).isInstanceOf(DataAccessResourceFailureException.class);
    }
    @Test
    void snapshotInsertMustNotHideOtherIntegrityErrors() {
        when(mapper.insertSnapshot(anyLong(), any(), anyLong(), any(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("invalid snapshot"));
        assertThatThrownBy(() -> store.insertSnapshot(event(), "hash", "{}", now)).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test
    void zeroAffectedRowsCannotBeReportedAsSuccess() {
        assertThatThrownBy(() -> store.updateSnapshot(event(), "hash", "{}", now, 1)).isInstanceOf(OptimisticLockingFailureException.class);
        assertThatThrownBy(() -> store.completeInbox(EVENT_ID, "APPLIED")).isInstanceOf(OptimisticLockingFailureException.class);
    }
}
