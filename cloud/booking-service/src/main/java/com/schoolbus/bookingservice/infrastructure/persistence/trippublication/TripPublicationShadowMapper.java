package com.schoolbus.bookingservice.infrastructure.persistence.trippublication;

import com.schoolbus.bookingservice.application.trippublication.TripPublicationShadowStore;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;

@Mapper
public interface TripPublicationShadowMapper {
    @Insert("""
            INSERT INTO booking_trip_publication_inbox (event_id, trip_id, payload_hash, outcome, received_at)
            VALUES (#{eventId}, #{tripId}, #{hash}, 'PROCESSING', #{now})
            """)
    int insertInbox(@Param("eventId") String eventId, @Param("tripId") long tripId,
                    @Param("hash") String hash, @Param("now") LocalDateTime now);

    @Select("""
            SELECT payload_hash, outcome FROM booking_trip_publication_inbox WHERE event_id=#{eventId} FOR SHARE
            """)
    TripPublicationShadowStore.Inbox lockInbox(String eventId);

    @Insert("""
            INSERT INTO booking_trip_publication_shadow
            (trip_id, trip_no, trip_version, payload_hash, snapshot_json, last_event_id, created_at, updated_at)
            VALUES (#{tripId}, #{tripNo}, #{version}, #{hash}, CAST(#{json} AS JSON), #{eventId}, #{now}, #{now})
            """)
    int insertSnapshot(@Param("tripId") long tripId, @Param("tripNo") String tripNo, @Param("version") long version,
                       @Param("hash") String hash, @Param("json") String json,
                       @Param("eventId") String eventId, @Param("now") LocalDateTime now);

    @Select("""
            SELECT trip_id, trip_no AS trip_number, trip_version, payload_hash
            FROM booking_trip_publication_shadow WHERE trip_id=#{tripId} FOR UPDATE
            """)
    TripPublicationShadowStore.Snapshot lockSnapshot(long tripId);

    @Update("""
            UPDATE booking_trip_publication_shadow SET trip_version=#{version}, payload_hash=#{hash},
                snapshot_json=CAST(#{json} AS JSON), last_event_id=#{eventId}, updated_at=#{now}
            WHERE trip_id=#{tripId} AND trip_version=#{expectedVersion}
            """)
    int updateSnapshot(@Param("tripId") long tripId, @Param("version") long version, @Param("hash") String hash,
                       @Param("json") String json, @Param("eventId") String eventId,
                       @Param("now") LocalDateTime now, @Param("expectedVersion") long expectedVersion);

    @Update("""
            UPDATE booking_trip_publication_inbox SET outcome=#{outcome} WHERE event_id=#{eventId} AND outcome='PROCESSING'
            """)
    int completeInbox(@Param("eventId") String eventId, @Param("outcome") String outcome);
}
