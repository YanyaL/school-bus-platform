package com.schoolbus.booking.infrastructure.scheduling;

import com.schoolbus.booking.application.booking.BookingExpirationApplicationService;
import com.schoolbus.booking.application.booking.BookingExpirationResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingExpirationSchedulerTest {

    @Test
    void shouldDelegateExpirationScan() {
        BookingExpirationApplicationService service =
                mock(BookingExpirationApplicationService.class);
        when(service.expireDueBookings())
                .thenReturn(new BookingExpirationResult(2, 2, 0));
        BookingExpirationScheduler scheduler =
                new BookingExpirationScheduler(service);

        scheduler.expireDueBookings();

        verify(service).expireDueBookings();
    }

    @Test
    void shouldKeepSchedulerAliveWhenScanFails() {
        BookingExpirationApplicationService service =
                mock(BookingExpirationApplicationService.class);
        when(service.expireDueBookings())
                .thenThrow(new IllegalStateException("database down"));
        BookingExpirationScheduler scheduler =
                new BookingExpirationScheduler(service);

        assertThatCode(scheduler::expireDueBookings)
                .doesNotThrowAnyException();
    }
}
