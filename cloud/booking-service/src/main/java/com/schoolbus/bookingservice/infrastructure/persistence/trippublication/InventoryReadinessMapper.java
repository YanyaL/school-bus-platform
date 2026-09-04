package com.schoolbus.bookingservice.infrastructure.persistence.trippublication;

import com.schoolbus.bookingservice.application.trippublication.InventoryReadinessCandidate;
import com.schoolbus.bookingservice.application.trippublication.InventoryReadinessObservation;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface InventoryReadinessMapper {
    @Select("""
            SELECT EXISTS (
                SELECT 1
                FROM booking_trip_inventory_readiness r
                INNER JOIN booking_trip_publication_shadow s
                    ON s.trip_id = r.trip_id
                   AND s.trip_version = r.publication_version
                WHERE r.trip_id = #{tripId}
                  AND r.publication_version = #{tripVersion}
                  AND r.status = 'READY'
            )
            """)
    boolean isReadyForPublication(
            @Param("tripId") long tripId,
            @Param("tripVersion") long tripVersion
    );

    @Select("""
            SELECT s.trip_id, s.trip_no AS trip_number,
                   s.trip_version AS publication_version,
                   CAST(s.snapshot_json AS CHAR) AS snapshot_json
            FROM booking_trip_publication_shadow s
            LEFT JOIN booking_trip_inventory_readiness r
              ON r.trip_id = s.trip_id
            WHERE r.trip_id IS NULL
               OR r.publication_version < s.trip_version
               OR r.status <> 'READY'
            ORDER BY s.updated_at, s.trip_id
            LIMIT #{limit}
            """)
    List<InventoryReadinessCandidate> findCandidates(
            @Param("limit") int limit
    );

    @Select("""
            SELECT total_seats
            FROM booking_trip_inventory
            WHERE trip_id = #{tripId}
            LIMIT 1
            """)
    Integer findInventoryTotal(@Param("tripId") long tripId);

    @Select("""
            SELECT seat_number
            FROM transport_trip_seat
            WHERE trip_id = #{tripId}
            ORDER BY seat_number
            """)
    List<String> findSeatNumbers(@Param("tripId") long tripId);

    @Insert("""
            INSERT INTO booking_trip_inventory_readiness (
                trip_id, trip_no, publication_version,
                expected_total_seats, observed_inventory_total,
                observed_seat_count, status, diagnostic_code,
                checked_at, ready_at, version
            ) VALUES (
                #{tripId}, #{tripNumber}, #{publicationVersion},
                #{expectedTotalSeats}, #{observedInventoryTotal},
                #{observedSeatCount}, #{status}, #{diagnosticCode},
                #{checkedAt}, #{readyAt}, 0
            )
            ON DUPLICATE KEY UPDATE
                trip_no = IF(publication_version < VALUES(publication_version)
                    OR (publication_version = VALUES(publication_version)
                        AND status <> 'READY'),
                    VALUES(trip_no), trip_no),
                expected_total_seats = IF(
                    publication_version < VALUES(publication_version)
                        OR (publication_version = VALUES(publication_version)
                            AND status <> 'READY'),
                    VALUES(expected_total_seats), expected_total_seats),
                observed_inventory_total = IF(
                    publication_version < VALUES(publication_version)
                        OR (publication_version = VALUES(publication_version)
                            AND status <> 'READY'),
                    VALUES(observed_inventory_total), observed_inventory_total),
                observed_seat_count = IF(
                    publication_version < VALUES(publication_version)
                        OR (publication_version = VALUES(publication_version)
                            AND status <> 'READY'),
                    VALUES(observed_seat_count), observed_seat_count),
                diagnostic_code = IF(
                    publication_version < VALUES(publication_version)
                        OR (publication_version = VALUES(publication_version)
                            AND status <> 'READY'),
                    VALUES(diagnostic_code), diagnostic_code),
                checked_at = IF(
                    publication_version < VALUES(publication_version)
                        OR (publication_version = VALUES(publication_version)
                            AND status <> 'READY'),
                    VALUES(checked_at), checked_at),
                ready_at = IF(publication_version < VALUES(publication_version)
                    OR (publication_version = VALUES(publication_version)
                        AND status <> 'READY'),
                    VALUES(ready_at), ready_at),
                version = IF(publication_version < VALUES(publication_version)
                    OR (publication_version = VALUES(publication_version)
                        AND status <> 'READY'),
                    version + 1, version),
                status = IF(publication_version < VALUES(publication_version)
                    OR (publication_version = VALUES(publication_version)
                        AND status <> 'READY'),
                    VALUES(status), status),
                publication_version = GREATEST(
                    publication_version, VALUES(publication_version))
            """)
    int saveObservation(
            @Param("tripId") long tripId,
            @Param("tripNumber") String tripNumber,
            @Param("publicationVersion") long publicationVersion,
            @Param("expectedTotalSeats") int expectedTotalSeats,
            @Param("observedInventoryTotal") Integer observedInventoryTotal,
            @Param("observedSeatCount") int observedSeatCount,
            @Param("status") String status,
            @Param("diagnosticCode") String diagnosticCode,
            @Param("checkedAt") LocalDateTime checkedAt,
            @Param("readyAt") LocalDateTime readyAt
    );
}
