-- Shadow-only observation state. No foreign keys or writes to live booking/inventory tables.
-- Migrations are still owned by Core in the shared-schema transitional deployment.
CREATE TABLE booking_trip_publication_inbox (
    event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    trip_id BIGINT NOT NULL,
    payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    outcome VARCHAR(24) NOT NULL,
    received_at DATETIME(3) NOT NULL,
    PRIMARY KEY (event_id),
    KEY idx_publication_inbox_trip (trip_id),
    CHECK (trip_id > 0),
    CHECK (outcome IN ('PROCESSING', 'APPLIED', 'STALE', 'ALREADY_APPLIED'))
) ENGINE=InnoDB;

CREATE TABLE booking_trip_publication_shadow (
    trip_id BIGINT NOT NULL,
    trip_no CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    trip_version BIGINT NOT NULL,
    payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    snapshot_json JSON NOT NULL,
    last_event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (trip_id),
    UNIQUE KEY uk_publication_shadow_trip_no (trip_no),
    CHECK (trip_id > 0),
    CHECK (trip_version > 0)
) ENGINE=InnoDB;
