package com.schoolbus.bookingservice.infrastructure.outbox;

import com.schoolbus.bookingservice.support.payment.infrastructure.outbox.OutboxRelayResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@Profile("!test")
@ConditionalOnProperty(
        prefix = "school-bus.messaging.outbox-relay",
        name = "enabled",
        havingValue = "true"
)
public class BookingRefundRequestedOutboxRelayScheduler {

    private static final Logger log = LoggerFactory.getLogger(
            BookingRefundRequestedOutboxRelayScheduler.class
    );

    private final BookingRefundRequestedOutboxRelay relay;

    public BookingRefundRequestedOutboxRelayScheduler(
            BookingRefundRequestedOutboxRelay relay
    ) {
        this.relay = Objects.requireNonNull(relay, "relay must not be null");
    }

    @Scheduled(
            initialDelayString =
                    "${school-bus.messaging.outbox-relay.initial-delay-ms:5000}",
            fixedDelayString =
                    "${school-bus.messaging.outbox-relay.fixed-delay-ms:1000}"
    )
    public void relay() {
        try {
            OutboxRelayResult result = relay.relayReadyEvents();
            if (result.claimed() > 0) {
                log.info(
                        "RefundRequested outbox relay completed: "
                                + "claimed={}, published={}, failed={}",
                        result.claimed(),
                        result.published(),
                        result.failed()
                );
            }
        } catch (RuntimeException exception) {
            log.error(
                    "RefundRequested outbox relay scan failed",
                    exception
            );
        }
    }
}
