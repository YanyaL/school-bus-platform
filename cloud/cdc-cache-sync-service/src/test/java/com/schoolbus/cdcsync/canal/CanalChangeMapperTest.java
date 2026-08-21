package com.schoolbus.cdcsync.canal;

import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.Message;
import com.schoolbus.cdcsync.event.CdcEvent;
import com.schoolbus.cdcsync.event.ConsumedEventRecordedEvent;
import com.schoolbus.cdcsync.event.TripCacheInvalidationEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CanalChangeMapperTest {

    private final CanalChangeMapper mapper = new CanalChangeMapper();

    @Test
    void shouldMapTripUpdateToOneCacheInvalidation() {
        Message message = message(
                "transport_trip",
                CanalEntry.EventType.UPDATE,
                row(column("status", "OPEN_FOR_BOOKING"))
        );

        List<CdcEvent> events = mapper.map(message);

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event).isInstanceOf(TripCacheInvalidationEvent.class);
            TripCacheInvalidationEvent invalidation =
                    (TripCacheInvalidationEvent) event;
            assertThat(invalidation.database()).isEqualTo("school_bus_platform");
            assertThat(invalidation.table()).isEqualTo("transport_trip");
            assertThat(invalidation.operation()).isEqualTo("UPDATE");
            assertThat(invalidation.binlogFile()).isEqualTo("binlog.000001");
            assertThat(invalidation.binlogOffset()).isEqualTo(123L);
        });
    }

    @Test
    void shouldMapConsumedEventInsertAndKeepStableCdcEventId() {
        Message message = message(
                "event_consumed",
                CanalEntry.EventType.INSERT,
                row(
                        column("consumer_name", "payment-refund"),
                        column("event_id", "event-42"),
                        column("consumed_at", "2026-08-21 03:40:00.123456")
                )
        );

        ConsumedEventRecordedEvent first =
                (ConsumedEventRecordedEvent) mapper.map(message).getFirst();
        ConsumedEventRecordedEvent second =
                (ConsumedEventRecordedEvent) mapper.map(message).getFirst();

        assertThat(first.eventId()).isEqualTo(second.eventId());
        assertThat(first.consumerName()).isEqualTo("payment-refund");
        assertThat(first.consumedEventId()).isEqualTo("event-42");
        assertThat(first.consumedAt()).isEqualTo(
                Instant.parse("2026-08-21T03:40:00.123456Z")
        );
    }

    @Test
    void shouldAcceptMySqlMillisecondTimestamp() {
        Message message = message(
                "event_consumed",
                CanalEntry.EventType.INSERT,
                row(
                        column("consumer_name", "payment-refund"),
                        column("event_id", "event-43"),
                        column("consumed_at", "2026-08-21 03:40:00.123")
                )
        );

        ConsumedEventRecordedEvent event =
                (ConsumedEventRecordedEvent) mapper.map(message).getFirst();

        assertThat(event.consumedAt()).isEqualTo(
                Instant.parse("2026-08-21T03:40:00.123Z")
        );
    }

    @Test
    void shouldIgnoreConsumedEventUpdatesAndUnrelatedTables() {
        Message consumedUpdate = message(
                "event_consumed",
                CanalEntry.EventType.UPDATE,
                row(column("event_id", "event-42"))
        );
        Message unrelated = message(
                "booking_order",
                CanalEntry.EventType.INSERT,
                row(column("id", "1"))
        );

        assertThat(mapper.map(consumedUpdate)).isEmpty();
        assertThat(mapper.map(unrelated)).isEmpty();
    }

    private Message message(
            String table,
            CanalEntry.EventType eventType,
            CanalEntry.RowData... rows
    ) {
        CanalEntry.Header header = CanalEntry.Header.newBuilder()
                .setSchemaName("school_bus_platform")
                .setTableName(table)
                .setLogfileName("binlog.000001")
                .setLogfileOffset(123L)
                .setExecuteTime(1_777_000_000_000L)
                .build();
        CanalEntry.RowChange change = CanalEntry.RowChange.newBuilder()
                .setEventType(eventType)
                .addAllRowDatas(List.of(rows))
                .build();
        CanalEntry.Entry entry = CanalEntry.Entry.newBuilder()
                .setHeader(header)
                .setEntryType(CanalEntry.EntryType.ROWDATA)
                .setStoreValue(change.toByteString())
                .build();
        return new Message(1L, List.of(entry));
    }

    private CanalEntry.RowData row(CanalEntry.Column... columns) {
        return CanalEntry.RowData.newBuilder()
                .addAllAfterColumns(List.of(columns))
                .build();
    }

    private CanalEntry.Column column(String name, String value) {
        return CanalEntry.Column.newBuilder()
                .setName(name)
                .setValue(value)
                .build();
    }
}
