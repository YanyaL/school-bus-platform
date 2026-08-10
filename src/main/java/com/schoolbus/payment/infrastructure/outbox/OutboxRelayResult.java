package com.schoolbus.payment.infrastructure.outbox;

public record OutboxRelayResult(
        int claimed,
        int published,
        int failed
) {

    public OutboxRelayResult {
        if (claimed < 0 || published < 0 || failed < 0) {
            throw new IllegalArgumentException(
                    "relay counts must not be negative"
            );
        }
        if (published + failed != claimed) {
            throw new IllegalArgumentException(
                    "published and failed must equal claimed"
            );
        }
    }
}
