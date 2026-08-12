package com.schoolbus.booking.application.booking;

import com.schoolbus.booking.domain.order.BookingStatus;

public record ListMyBookingsQuery(
        long userId,
        BookingStatus status,
        int page,
        int size,
        boolean sortByCreatedAtAscending
) {

    public ListMyBookingsQuery {
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (size <= 0 || size > 100) {
            throw new IllegalArgumentException(
                    "size must be between 1 and 100"
            );
        }
    }

    public int offset() {
        return page * size;
    }
}
