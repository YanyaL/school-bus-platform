package com.schoolbus.booking.infrastructure.messaging;

import com.schoolbus.booking.config.ConditionalOnEmbeddedBooking;

import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@ConditionalOnEmbeddedBooking
@Component
public class PaymentSucceededRetryAttemptResolver {

    private static final String DEATH_HEADER = "x-death";

    public int completedRetries(Message message, String retryQueue) {
        Message checked = Objects.requireNonNull(
                message,
                "message must not be null"
        );
        String queue = requireText(retryQueue);
        Object header = checked.getMessageProperties()
                .getHeaders()
                .get(DEATH_HEADER);
        if (!(header instanceof List<?> deaths)) {
            return 0;
        }
        long retries = 0L;
        for (Object item : deaths) {
            if (!(item instanceof Map<?, ?> death)
                    || !queue.equals(String.valueOf(death.get("queue")))) {
                continue;
            }
            Object count = death.get("count");
            if (count instanceof Number number) {
                retries += number.longValue();
            }
        }
        return retries > Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : (int) retries;
    }

    private String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "retryQueue must not be blank"
            );
        }
        return value.strip();
    }
}
