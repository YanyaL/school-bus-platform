-- Local demo seed for Swagger / E2E walkthrough.
-- Run after Flyway migrations (first application startup).
-- Demo trip id: 9001, seats A01-A10, inventory 10/10.

USE school_bus_platform;

DELETE FROM booking_trip_inventory WHERE trip_id = 9001;
DELETE FROM transport_trip_seat WHERE trip_id = 9001;
DELETE FROM transport_trip WHERE id = 9001;
DELETE FROM transport_route WHERE id = 9001;
DELETE FROM transport_vehicle WHERE id = 9001;

INSERT INTO transport_vehicle (
    id, vehicle_no, license_plate, seat_count,
    status, version, created_at, updated_at
) VALUES (
    9001,
    '00000000-0000-4000-8000-000000009001',
    'DEMO-9001',
    10,
    'ENABLED',
    0,
    UTC_TIMESTAMP(3),
    UTC_TIMESTAMP(3)
);

INSERT INTO transport_route (
    id, route_no, route_code,
    departure_campus, arrival_campus,
    estimated_duration_minutes, status,
    version, created_at, updated_at
) VALUES (
    9001,
    '00000000-0000-4000-8000-000000009002',
    'DEMO-CAMPUS-A-B',
    'MAIN',
    'EAST',
    45,
    'ENABLED',
    0,
    UTC_TIMESTAMP(3),
    UTC_TIMESTAMP(3)
);

INSERT INTO transport_trip (
    id, trip_no, vehicle_id, route_id,
    departure_time, booking_deadline, price,
    status, version, created_at, updated_at
) VALUES (
    9001,
    '00000000-0000-4000-8000-000000009003',
    9001,
    9001,
    DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 2 DAY),
    DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 1 DAY),
    5.50,
    'OPEN_FOR_BOOKING',
    0,
    UTC_TIMESTAMP(3),
    UTC_TIMESTAMP(3)
);

INSERT INTO transport_trip_seat (
    trip_id, seat_number, status,
    version, created_at, updated_at
)
SELECT
    9001,
    CONCAT('A', LPAD(n, 2, '0')),
    'AVAILABLE',
    0,
    UTC_TIMESTAMP(3),
    UTC_TIMESTAMP(3)
FROM (
    SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
    UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8
    UNION ALL SELECT 9 UNION ALL SELECT 10
) numbers;

INSERT INTO booking_trip_inventory (
    trip_id, total_seats, available_seats,
    version, created_at, updated_at
) VALUES (
    9001,
    10,
    10,
    0,
    UTC_TIMESTAMP(3),
    UTC_TIMESTAMP(3)
);
