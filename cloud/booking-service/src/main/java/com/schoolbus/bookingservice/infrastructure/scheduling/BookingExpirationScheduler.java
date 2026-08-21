package com.schoolbus.bookingservice.infrastructure.scheduling;

import com.schoolbus.bookingservice.application.booking.BookingExpirationApplicationService;
import com.schoolbus.bookingservice.application.booking.BookingExpirationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@Profile("!test")
@ConditionalOnProperty(
        prefix = "school-bus.booking.expiration.scheduler",
        name = "enabled",
        havingValue = "true"
)
public class BookingExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(
            BookingExpirationScheduler.class
    );

    private final BookingExpirationApplicationService applicationService;

    public BookingExpirationScheduler(
            BookingExpirationApplicationService applicationService
    ) {
        this.applicationService = Objects.requireNonNull(
                applicationService,
                "applicationService must not be null"
        );
    }

    @Scheduled(
            initialDelayString =
                    "${school-bus.booking.expiration.scheduler.initial-delay-ms:15000}",
            fixedDelayString =
                    "${school-bus.booking.expiration.scheduler.fixed-delay-ms:10000}"
    )
    public void expireDueBookings() {
        try {
            BookingExpirationResult result = applicationService
                    .expireDueBookings();
            log.info(
                    "Booking expiration completed: scanned={}, "
                            + "expired={}, conflicts={}",
                    result.scanned(),
                    result.expired(),
                    result.conflicts()
            );
        } catch (RuntimeException exception) {
            log.error("Booking expiration scan failed", exception);
        }
    }
}
