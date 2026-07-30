CREATE TABLE iam_account (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id         BIGINT UNSIGNED NOT NULL,
    student_number  VARCHAR(32) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    status          VARCHAR(16) NOT NULL,
    version         BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at      DATETIME(3) NOT NULL,
    updated_at      DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_account_user_id (user_id),
    UNIQUE KEY uk_iam_account_student_number (student_number),
    CONSTRAINT ck_iam_account_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE iam_account_role (
    account_id  BIGINT UNSIGNED NOT NULL,
    role_code   VARCHAR(32) NOT NULL,
    created_at  DATETIME(3) NOT NULL,
    PRIMARY KEY (account_id, role_code),
    CONSTRAINT fk_iam_role_account
        FOREIGN KEY (account_id) REFERENCES iam_account (id),
    CONSTRAINT ck_iam_role_code
        CHECK (role_code IN ('STUDENT', 'ADMIN'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE student_profile (
    user_id       BIGINT UNSIGNED NOT NULL,
    name          VARCHAR(50) NOT NULL,
    phone_number  VARCHAR(20) NULL,
    status        VARCHAR(16) NOT NULL,
    version       BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at    DATETIME(3) NOT NULL,
    updated_at    DATETIME(3) NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT ck_student_profile_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE transport_vehicle (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    vehicle_no     CHAR(36) NOT NULL,
    license_plate  VARCHAR(20) NOT NULL,
    seat_count     SMALLINT UNSIGNED NOT NULL,
    status         VARCHAR(16) NOT NULL,
    version        BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at     DATETIME(3) NOT NULL,
    updated_at     DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_transport_vehicle_no (vehicle_no),
    UNIQUE KEY uk_transport_vehicle_license_plate (license_plate),
    CONSTRAINT ck_transport_vehicle_seat_count
        CHECK (seat_count > 0),
    CONSTRAINT ck_transport_vehicle_status
        CHECK (status IN ('ENABLED', 'DISABLED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE transport_vehicle_seat (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    vehicle_id   BIGINT UNSIGNED NOT NULL,
    seat_number  VARCHAR(10) NOT NULL,
    created_at   DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_vehicle_seat_number (vehicle_id, seat_number),
    CONSTRAINT fk_vehicle_seat_vehicle
        FOREIGN KEY (vehicle_id) REFERENCES transport_vehicle (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE transport_route (
    id                          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    route_no                    CHAR(36) NOT NULL,
    route_code                  VARCHAR(32) NOT NULL,
    departure_campus            VARCHAR(64) NOT NULL,
    arrival_campus              VARCHAR(64) NOT NULL,
    estimated_duration_minutes  SMALLINT UNSIGNED NOT NULL,
    status                      VARCHAR(16) NOT NULL,
    version                     BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at                  DATETIME(3) NOT NULL,
    updated_at                  DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_transport_route_no (route_no),
    UNIQUE KEY uk_transport_route_code (route_code),
    KEY idx_transport_route_direction
        (departure_campus, arrival_campus, status),
    CONSTRAINT ck_transport_route_direction
        CHECK (departure_campus <> arrival_campus),
    CONSTRAINT ck_transport_route_duration
        CHECK (estimated_duration_minutes > 0),
    CONSTRAINT ck_transport_route_status
        CHECK (status IN ('ENABLED', 'DISABLED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE transport_trip (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    trip_no           CHAR(36) NOT NULL,
    vehicle_id        BIGINT UNSIGNED NOT NULL,
    route_id          BIGINT UNSIGNED NOT NULL,
    departure_time    DATETIME(3) NOT NULL,
    booking_deadline  DATETIME(3) NOT NULL,
    price             DECIMAL(10,2) NOT NULL,
    status            VARCHAR(32) NOT NULL,
    version           BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at        DATETIME(3) NOT NULL,
    updated_at        DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_transport_trip_no (trip_no),
    UNIQUE KEY uk_transport_trip_vehicle_departure
        (vehicle_id, departure_time),
    KEY idx_transport_trip_route_departure
        (route_id, departure_time, status),
    KEY idx_transport_trip_status_departure
        (status, departure_time),
    CONSTRAINT fk_transport_trip_vehicle
        FOREIGN KEY (vehicle_id) REFERENCES transport_vehicle (id),
    CONSTRAINT fk_transport_trip_route
        FOREIGN KEY (route_id) REFERENCES transport_route (id),
    CONSTRAINT ck_transport_trip_time
        CHECK (booking_deadline < departure_time),
    CONSTRAINT ck_transport_trip_price
        CHECK (price >= 0),
    CONSTRAINT ck_transport_trip_status
        CHECK (
            status IN (
                'DRAFT',
                'OPEN_FOR_BOOKING',
                'CLOSED',
                'DEPARTED',
                'COMPLETED',
                'CANCELLED'
            )
        )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE transport_trip_seat (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    trip_id               BIGINT UNSIGNED NOT NULL,
    seat_number           VARCHAR(10) NOT NULL,
    status                VARCHAR(16) NOT NULL,
    locked_by_order_no    CHAR(36) NULL,
    locked_by_user_id     BIGINT UNSIGNED NULL,
    lock_expires_at       DATETIME(3) NULL,
    version               BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at            DATETIME(3) NOT NULL,
    updated_at            DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_trip_seat_number (trip_id, seat_number),
    KEY idx_trip_seat_status (trip_id, status),
    KEY idx_trip_seat_locked_order (locked_by_order_no),
    KEY idx_trip_seat_expiration (status, lock_expires_at),
    CONSTRAINT fk_trip_seat_trip
        FOREIGN KEY (trip_id) REFERENCES transport_trip (id),
    CONSTRAINT ck_trip_seat_status
        CHECK (status IN ('AVAILABLE', 'LOCKED', 'SOLD'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE booking_order (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_no         CHAR(36) NOT NULL,
    request_no       VARCHAR(64) NOT NULL,
    user_id          BIGINT UNSIGNED NOT NULL,
    trip_id          BIGINT UNSIGNED NOT NULL,
    seat_number      VARCHAR(10) NOT NULL,
    price_snapshot   DECIMAL(10,2) NOT NULL,
    status           VARCHAR(32) NOT NULL,
    expires_at       DATETIME(3) NOT NULL,
    payment_no       CHAR(36) NULL,
    paid_at          DATETIME(3) NULL,
    cancelled_at     DATETIME(3) NULL,
    cancel_reason    VARCHAR(32) NULL,
    version          BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at       DATETIME(3) NOT NULL,
    updated_at       DATETIME(3) NOT NULL,
    active_marker    TINYINT
        GENERATED ALWAYS AS (
            CASE
                WHEN status IN ('PENDING_PAYMENT', 'PAID') THEN 1
                ELSE NULL
            END
        ) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_booking_order_no (order_no),
    UNIQUE KEY uk_booking_order_request_no (request_no),
    UNIQUE KEY uk_booking_user_trip_active
        (user_id, trip_id, active_marker),
    UNIQUE KEY uk_booking_trip_seat_active
        (trip_id, seat_number, active_marker),
    KEY idx_booking_order_user_created
        (user_id, created_at DESC),
    KEY idx_booking_order_expiration
        (status, expires_at),
    KEY idx_booking_order_trip
        (trip_id, status),
    CONSTRAINT ck_booking_order_price
        CHECK (price_snapshot >= 0),
    CONSTRAINT ck_booking_order_status
        CHECK (status IN ('PENDING_PAYMENT', 'PAID', 'CANCELLED')),
    CONSTRAINT ck_booking_order_cancel_reason
        CHECK (
            cancel_reason IS NULL
            OR cancel_reason IN ('USER_CANCELLED', 'PAYMENT_TIMEOUT')
        )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE payment_record (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    payment_no       CHAR(36) NOT NULL,
    request_no       VARCHAR(64) NOT NULL,
    order_no         CHAR(36) NOT NULL,
    amount           DECIMAL(10,2) NOT NULL,
    status           VARCHAR(16) NOT NULL,
    failure_reason   VARCHAR(255) NULL,
    completed_at     DATETIME(3) NULL,
    version          BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at       DATETIME(3) NOT NULL,
    updated_at       DATETIME(3) NOT NULL,
    success_marker   TINYINT
        GENERATED ALWAYS AS (
            CASE WHEN status = 'SUCCEEDED' THEN 1 ELSE NULL END
        ) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_no (payment_no),
    UNIQUE KEY uk_payment_request_no (request_no),
    UNIQUE KEY uk_payment_order_success
        (order_no, success_marker),
    KEY idx_payment_order_status (order_no, status),
    CONSTRAINT ck_payment_amount
        CHECK (amount >= 0),
    CONSTRAINT ck_payment_status
        CHECK (status IN ('PROCESSING', 'SUCCEEDED', 'FAILED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE event_outbox (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_id           CHAR(36) NOT NULL,
    context_name       VARCHAR(32) NOT NULL,
    aggregate_type     VARCHAR(64) NOT NULL,
    aggregate_id       VARCHAR(64) NOT NULL,
    aggregate_version  BIGINT UNSIGNED NOT NULL,
    event_type         VARCHAR(64) NOT NULL,
    payload            JSON NOT NULL,
    trace_id           VARCHAR(64) NULL,
    status             VARCHAR(16) NOT NULL,
    retry_count        INT UNSIGNED NOT NULL DEFAULT 0,
    next_retry_at      DATETIME(3) NULL,
    occurred_at        DATETIME(3) NOT NULL,
    created_at         DATETIME(3) NOT NULL,
    published_at       DATETIME(3) NULL,
    version            BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_outbox_event_id (event_id),
    KEY idx_event_outbox_publish
        (status, next_retry_at, id),
    KEY idx_event_outbox_aggregate
        (aggregate_type, aggregate_id, aggregate_version),
    CONSTRAINT ck_event_outbox_status
        CHECK (status IN ('NEW', 'PUBLISHED', 'FAILED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE event_consumed (
    consumer_name  VARCHAR(64) NOT NULL,
    event_id       CHAR(36) NOT NULL,
    consumed_at    DATETIME(3) NOT NULL,
    PRIMARY KEY (consumer_name, event_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
