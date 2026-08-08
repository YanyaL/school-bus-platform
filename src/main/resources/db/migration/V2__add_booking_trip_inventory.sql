ALTER TABLE booking_order
    DROP CHECK ck_booking_order_status;

ALTER TABLE booking_order
    ADD CONSTRAINT ck_booking_order_status
        CHECK (
            status IN (
                'PENDING_PAYMENT',
                'PAID',
                'CANCELLED',
                'REFUNDED'
            )
        );

CREATE TABLE booking_trip_inventory (
    trip_id          BIGINT UNSIGNED NOT NULL,
    total_seats      SMALLINT UNSIGNED NOT NULL,
    available_seats  SMALLINT UNSIGNED NOT NULL,
    version          BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at       DATETIME(3) NOT NULL,
    updated_at       DATETIME(3) NOT NULL,
    PRIMARY KEY (trip_id),
    CONSTRAINT fk_booking_inventory_trip
        FOREIGN KEY (trip_id) REFERENCES transport_trip (id),
    CONSTRAINT ck_booking_inventory_total
        CHECK (total_seats > 0),
    CONSTRAINT ck_booking_inventory_available
        CHECK (
            available_seats >= 0
            AND available_seats <= total_seats
        )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
