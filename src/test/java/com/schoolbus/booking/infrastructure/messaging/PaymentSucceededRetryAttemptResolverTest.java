package com.schoolbus.booking.infrastructure.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentSucceededRetryAttemptResolverTest {

    private final PaymentSucceededRetryAttemptResolver resolver =
            new PaymentSucceededRetryAttemptResolver();

    @Test
    void shouldReturnZeroWithoutDeathHeader() {
        assertThat(resolver.completedRetries(
                message(null),
                "schoolbus.booking.payment-succeeded.retry"
        )).isZero();
    }

    @Test
    void shouldCountOnlyPaymentSucceededRetryDeaths() {
        List<Map<String, Object>> deaths = List.of(
                Map.of(
                        "queue",
                        "schoolbus.booking.payment-succeeded.retry",
                        "count",
                        2L
                ),
                Map.of("queue", "another.queue", "count", 9L)
        );

        assertThat(resolver.completedRetries(
                message(deaths),
                "schoolbus.booking.payment-succeeded.retry"
        )).isEqualTo(2);
    }

    private Message message(Object deaths) {
        MessageProperties properties = new MessageProperties();
        if (deaths != null) {
            properties.setHeader("x-death", deaths);
        }
        return new Message(new byte[0], properties);
    }
}
