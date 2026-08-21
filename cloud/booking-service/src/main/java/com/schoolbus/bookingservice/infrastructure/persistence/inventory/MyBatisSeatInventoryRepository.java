package com.schoolbus.bookingservice.infrastructure.persistence.inventory;

import com.schoolbus.bookingservice.domain.inventory.SeatInventory;
import com.schoolbus.bookingservice.domain.inventory.SeatInventoryRepository;
import com.schoolbus.bookingservice.domain.trip.TripReference;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;

@Repository
@Profile("!test")
public class MyBatisSeatInventoryRepository
        implements SeatInventoryRepository {

    private static final ZoneOffset DATABASE_ZONE = ZoneOffset.UTC;

    private final SeatInventoryMapper seatInventoryMapper;

    public MyBatisSeatInventoryRepository(
            SeatInventoryMapper seatInventoryMapper
    ) {
        this.seatInventoryMapper = Objects.requireNonNull(
                seatInventoryMapper,
                "seatInventoryMapper must not be null"
        );
    }

    @Override
    public SeatInventory save(SeatInventory seatInventory) {
        SeatInventory validatedInventory = Objects.requireNonNull(
                seatInventory,
                "seatInventory must not be null"
        );
        SeatInventoryDataObject dataObject = toDataObject(
                validatedInventory
        );

        if (validatedInventory.version() == 0L) {
            int insertedRows = seatInventoryMapper.insertInventory(
                    dataObject
            );
            if (insertedRows != 1) {
                throw new IllegalStateException(
                        "failed to insert seat inventory"
                );
            }
            return validatedInventory;
        }

        long expectedVersion = validatedInventory.version() - 1L;
        int updatedRows = seatInventoryMapper.updateWithVersion(
                dataObject,
                expectedVersion
        );
        if (updatedRows != 1) {
            throw new OptimisticLockingFailureException(
                    "seat inventory was modified by another request"
            );
        }
        return validatedInventory;
    }

    @Override
    public Optional<SeatInventory> findByTripReference(
            TripReference tripReference
    ) {
        TripReference validatedReference = Objects.requireNonNull(
                tripReference,
                "tripReference must not be null"
        );
        SeatInventoryDataObject dataObject =
                seatInventoryMapper.selectByTripId(
                        validatedReference.value()
                );
        return dataObject == null
                ? Optional.empty()
                : Optional.of(toDomain(dataObject));
    }

    private SeatInventoryDataObject toDataObject(
            SeatInventory inventory
    ) {
        SeatInventoryDataObject dataObject =
                new SeatInventoryDataObject();
        dataObject.setTripId(inventory.tripReference().value());
        dataObject.setTotalSeats(inventory.totalSeats());
        dataObject.setAvailableSeats(inventory.availableSeats());
        dataObject.setVersion(inventory.version());
        dataObject.setCreatedAt(
                toDatabaseTime(inventory.createdAt())
        );
        dataObject.setUpdatedAt(
                toDatabaseTime(inventory.updatedAt())
        );
        return dataObject;
    }

    private SeatInventory toDomain(
            SeatInventoryDataObject dataObject
    ) {
        return SeatInventory.restore(
                TripReference.of(dataObject.getTripId()),
                dataObject.getTotalSeats(),
                dataObject.getAvailableSeats(),
                dataObject.getVersion(),
                toInstant(dataObject.getCreatedAt()),
                toInstant(dataObject.getUpdatedAt())
        );
    }

    private LocalDateTime toDatabaseTime(Instant instant) {
        return LocalDateTime.ofInstant(
                Objects.requireNonNull(
                        instant,
                        "instant must not be null"
                ),
                DATABASE_ZONE
        );
    }

    private Instant toInstant(LocalDateTime localDateTime) {
        return localDateTime.toInstant(DATABASE_ZONE);
    }
}
