package com.schoolbus.transport.infrastructure.outbox;

import com.schoolbus.payment.infrastructure.messaging.OutboxRelayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@ConditionalOnProperty(prefix = "school-bus.transport.publication-events",
        name = {"enabled", "relay-enabled"}, havingValue = "true")
public class TripPublicationOutboxRelayScheduler {
    private static final Logger log = LoggerFactory.getLogger(TripPublicationOutboxRelayScheduler.class);
    private final TripPublicationOutboxRelay relay;
    private final OutboxRelayProperties properties;

    public TripPublicationOutboxRelayScheduler(TripPublicationOutboxRelay relay, OutboxRelayProperties properties) {
        this.relay = java.util.Objects.requireNonNull(relay);
        this.properties = java.util.Objects.requireNonNull(properties);
    }

    @Scheduled(initialDelayString = "${school-bus.messaging.outbox-relay.initial-delay-ms:5000}",
            fixedDelayString = "${school-bus.messaging.outbox-relay.fixed-delay-ms:1000}")
    public void relay() {
        if (!properties.enabled()) {
            return;
        }
        try {
            var result = relay.relayReadyEvents();
            if (result.claimed() > 0) {
                log.info("TripPublished relay: {}", result);
            }
        } catch (RuntimeException exception) {
            log.error("TripPublished outbox scan failed", exception);
        }
    }
}
