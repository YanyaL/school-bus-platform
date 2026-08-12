package com.schoolbus.booking.application.booking;

import com.schoolbus.booking.domain.order.BookingOrderRepository;
import com.schoolbus.shared.domain.identity.UserId;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@Profile("!test")
public class BookingQueryApplicationService {

    private final BookingOrderRepository bookingOrderRepository;

    public BookingQueryApplicationService(
            BookingOrderRepository bookingOrderRepository
    ) {
        this.bookingOrderRepository = Objects.requireNonNull(
                bookingOrderRepository,
                "bookingOrderRepository must not be null"
        );
    }

    public List<BookingSummaryView> listMyBookings(
            ListMyBookingsQuery query
    ) {
        ListMyBookingsQuery validatedQuery = Objects.requireNonNull(
                query,
                "query must not be null"
        );
        UserId userId = UserId.of(validatedQuery.userId());
        return bookingOrderRepository
                .findByUserId(
                        userId,
                        validatedQuery.status(),
                        validatedQuery.offset(),
                        validatedQuery.size(),
                        validatedQuery.sortByCreatedAtAscending()
                )
                .stream()
                .map(BookingSummaryView::from)
                .toList();
    }

    public long countMyBookings(ListMyBookingsQuery query) {
        ListMyBookingsQuery validatedQuery = Objects.requireNonNull(
                query,
                "query must not be null"
        );
        return bookingOrderRepository.countByUserId(
                UserId.of(validatedQuery.userId()),
                validatedQuery.status()
        );
    }
}
