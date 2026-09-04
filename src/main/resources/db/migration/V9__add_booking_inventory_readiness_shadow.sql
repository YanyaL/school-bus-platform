-- Booking-owned readiness observation. It does not initialize or mutate live inventory.
CREATE TABLE booking_trip_inventory_readiness (
    trip_id BIGINT NOT NULL,
    trip_no CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    publication_version BIGINT NOT NULL,
    expected_total_seats INT NOT NULL,
    observed_inventory_total INT NULL,
    observed_seat_count INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    diagnostic_code VARCHAR(40) NULL,
    checked_at DATETIME(3) NOT NULL,
    ready_at DATETIME(3) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (trip_id),
    UNIQUE KEY uk_inventory_readiness_trip_no (trip_no),
    KEY idx_inventory_readiness_status (status, checked_at),
    CHECK (trip_id > 0),
    CHECK (publication_version > 0),
    CHECK (expected_total_seats > 0),
    CHECK (observed_inventory_total IS NULL OR observed_inventory_total >= 0),
    CHECK (observed_seat_count >= 0),
    CHECK (status IN ('WAITING', 'READY'))
) ENGINE=InnoDB;
