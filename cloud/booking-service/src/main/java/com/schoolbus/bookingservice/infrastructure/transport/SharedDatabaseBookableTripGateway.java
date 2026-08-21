package com.schoolbus.bookingservice.infrastructure.transport;

import com.schoolbus.bookingservice.application.booking.BookableTripGateway;
import com.schoolbus.bookingservice.application.booking.BookableTripSnapshot;
import com.schoolbus.bookingservice.domain.order.BookingAmount;
import com.schoolbus.bookingservice.domain.trip.PublicTripNumber;
import com.schoolbus.bookingservice.domain.trip.TripReference;
import com.schoolbus.bookingservice.infrastructure.persistence.trip.BookableTripMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;

@Component
@Profile("!test")
public class SharedDatabaseBookableTripGateway implements BookableTripGateway {

    private static final ZoneOffset DATABASE_ZONE = ZoneOffset.UTC;
    private static final String OPEN_FOR_BOOKING = "OPEN_FOR_BOOKING";

    private final BookableTripMapper mapper;

    public SharedDatabaseBookableTripGateway(BookableTripMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    @Override
    public Optional<BookableTripSnapshot> findByTripNumber(PublicTripNumber tripNumber) {
        PublicTripNumber validated = Objects.requireNonNull(tripNumber, "tripNumber must not be null");
        BookableTripMapper.TripRow row = mapper.selectByTripNumber(validated.toString());
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(new BookableTripSnapshot(
                TripReference.of(row.id()),
                PublicTripNumber.of(row.tripNo()),
                new BookingAmount(row.price()),
                toInstant(row.departureTime()),
                toInstant(row.bookingDeadline()),
                OPEN_FOR_BOOKING.equals(row.status())
        ));
    }

    private Instant toInstant(LocalDateTime value) {
        return value.atZone(DATABASE_ZONE).toInstant();
    }
}
