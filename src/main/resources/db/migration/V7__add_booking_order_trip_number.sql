-- Add immutable public trip number snapshot on booking_order.
-- Internal trip_id remains for seat locks, inventory, and uniqueness.

ALTER TABLE booking_order
    ADD COLUMN trip_no CHAR(36) NULL AFTER trip_id;

UPDATE booking_order bo
    INNER JOIN transport_trip tt ON tt.id = bo.trip_id
SET bo.trip_no = tt.trip_no
WHERE bo.trip_no IS NULL;

ALTER TABLE booking_order
    MODIFY COLUMN trip_no CHAR(36) NOT NULL;

CREATE INDEX idx_booking_order_trip_no
    ON booking_order (trip_no);
