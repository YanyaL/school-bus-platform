package com.schoolbus.cdcsync.canal;

import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.Message;
import com.google.protobuf.InvalidProtocolBufferException;
import com.schoolbus.cdcsync.event.CdcEvent;
import com.schoolbus.cdcsync.event.ConsumedEventRecordedEvent;
import com.schoolbus.cdcsync.event.TripCacheInvalidationEvent;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class CanalChangeMapper {

    private static final String TRIP_TABLE = "transport_trip";
    private static final String CONSUMED_EVENT_TABLE = "event_consumed";
    private static final DateTimeFormatter MYSQL_TIMESTAMP =
            new DateTimeFormatterBuilder()
                    .appendPattern("yyyy-MM-dd HH:mm:ss")
                    .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
                    .toFormatter();

    public List<CdcEvent> map(Message message) {
        List<CdcEvent> events = new ArrayList<>();
        for (CanalEntry.Entry entry : message.getEntries()) {
            if (entry.getEntryType() != CanalEntry.EntryType.ROWDATA) {
                continue;
            }
            mapEntry(entry, events);
        }
        return List.copyOf(events);
    }

    private void mapEntry(CanalEntry.Entry entry, List<CdcEvent> events) {
        CanalEntry.RowChange change;
        try {
            change = CanalEntry.RowChange.parseFrom(entry.getStoreValue());
        } catch (InvalidProtocolBufferException exception) {
            throw new IllegalStateException("cannot parse Canal row change", exception);
        }

        String table = entry.getHeader().getTableName();
        if (TRIP_TABLE.equalsIgnoreCase(table)) {
            events.add(toTripInvalidation(entry, change.getEventType()));
            return;
        }
        if (!CONSUMED_EVENT_TABLE.equalsIgnoreCase(table)
                || change.getEventType() != CanalEntry.EventType.INSERT) {
            return;
        }

        int rowIndex = 0;
        for (CanalEntry.RowData row : change.getRowDatasList()) {
            Map<String, String> columns = columns(row.getAfterColumnsList());
            events.add(toConsumedEvent(entry, columns, rowIndex++));
        }
    }

    private TripCacheInvalidationEvent toTripInvalidation(
            CanalEntry.Entry entry,
            CanalEntry.EventType operation
    ) {
        CanalEntry.Header header = entry.getHeader();
        return new TripCacheInvalidationEvent(
                stableId(header, operation.name(), 0),
                header.getSchemaName(),
                header.getTableName(),
                operation.name(),
                occurredAt(header),
                header.getLogfileName(),
                header.getLogfileOffset()
        );
    }

    private ConsumedEventRecordedEvent toConsumedEvent(
            CanalEntry.Entry entry,
            Map<String, String> columns,
            int rowIndex
    ) {
        CanalEntry.Header header = entry.getHeader();
        return new ConsumedEventRecordedEvent(
                stableId(header, CanalEntry.EventType.INSERT.name(), rowIndex),
                requireColumn(columns, "consumer_name"),
                requireColumn(columns, "event_id"),
                parseDatabaseInstant(requireColumn(columns, "consumed_at")),
                occurredAt(header),
                header.getLogfileName(),
                header.getLogfileOffset()
        );
    }

    private Map<String, String> columns(List<CanalEntry.Column> source) {
        Map<String, String> values = new HashMap<>();
        for (CanalEntry.Column column : source) {
            values.put(column.getName().toLowerCase(), column.getValue());
        }
        return values;
    }

    private String requireColumn(Map<String, String> columns, String name) {
        String value = columns.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Canal row is missing column " + name);
        }
        return value;
    }

    private Instant occurredAt(CanalEntry.Header header) {
        long executeTime = header.getExecuteTime();
        return executeTime > 0 ? Instant.ofEpochMilli(executeTime) : Instant.EPOCH;
    }

    private Instant parseDatabaseInstant(String value) {
        return LocalDateTime.parse(value, MYSQL_TIMESTAMP).toInstant(ZoneOffset.UTC);
    }

    private String stableId(
            CanalEntry.Header header,
            String operation,
            int rowIndex
    ) {
        String source = String.join(
                ":",
                header.getLogfileName(),
                Long.toString(header.getLogfileOffset()),
                header.getSchemaName(),
                header.getTableName(),
                operation,
                Integer.toString(rowIndex)
        );
        return UUID.nameUUIDFromBytes(
                source.getBytes(StandardCharsets.UTF_8)
        ).toString();
    }
}
