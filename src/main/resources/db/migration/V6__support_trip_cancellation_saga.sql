ALTER TABLE transport_trip
    DROP CHECK ck_transport_trip_status;

ALTER TABLE transport_trip
    ADD CONSTRAINT ck_transport_trip_status
        CHECK (
            status IN (
                'DRAFT',
                'OPEN_FOR_BOOKING',
                'CLOSED',
                'CANCELLATION_PENDING',
                'DEPARTED',
                'COMPLETED',
                'CANCELLED'
            )
        );

ALTER TABLE booking_order
    DROP CHECK ck_booking_order_status;

ALTER TABLE booking_order
    ADD CONSTRAINT ck_booking_order_status
        CHECK (
            status IN (
                'PENDING_PAYMENT',
                'PAID',
                'REFUND_PENDING',
                'CANCELLED',
                'REFUNDED'
            )
        );

CREATE TABLE booking_trip_cancellation_saga (
    trip_id           BIGINT UNSIGNED NOT NULL,
    request_event_id  CHAR(36) NOT NULL,
    pending_refunds   INT UNSIGNED NOT NULL,
    status            VARCHAR(16) NOT NULL,
    version           BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at        DATETIME(3) NOT NULL,
    updated_at        DATETIME(3) NOT NULL,
    PRIMARY KEY (trip_id),
    UNIQUE KEY uk_trip_cancellation_request_event (request_event_id),
    CONSTRAINT fk_trip_cancellation_saga_trip
        FOREIGN KEY (trip_id) REFERENCES transport_trip (id),
    CONSTRAINT ck_trip_cancellation_pending_refunds
        CHECK (pending_refunds >= 0),
    CONSTRAINT ck_trip_cancellation_saga_status
        CHECK (status IN ('PROCESSING', 'SETTLED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

ALTER TABLE booking_order
    DROP CHECK ck_booking_order_cancel_reason;

ALTER TABLE booking_order
    ADD CONSTRAINT ck_booking_order_cancel_reason
        CHECK (
            cancel_reason IS NULL
            OR cancel_reason IN (
                'USER_CANCELLED',
                'PAYMENT_TIMEOUT',
                'TRIP_CANCELLED'
            )
        );
