package com.schoolbus.bookingservice.infrastructure.messaging.trippublication;

import com.schoolbus.bookingservice.application.trippublication.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import static com.schoolbus.bookingservice.trippublication.PublicationFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TripPublicationShadowListenerTest {
    private final TripPublicationShadowTransaction transaction = mock(TripPublicationShadowTransaction.class);
    private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();
    private final TripPublicationShadowListener listener = new TripPublicationShadowListener(new TripPublicationMessageDecoder(JSON), transaction, metrics);

    @Test
    void recordsOnlyCompletedTransactionOutcomeWithLowCardinalityLabels() {
        when(transaction.observe(any())).thenReturn(TripPublicationShadowTransaction.Outcome.DUPLICATE);
        listener.consume(message());
        assertThat(metrics.get("schoolbus.booking.trip_publication.shadow").tag("outcome", "DUPLICATE").counter().count()).isEqualTo(1);
    }
    @Test
    void failurePropagatesToContainerWithoutSuccessfulCompletionMetric() {
        when(transaction.observe(any())).thenThrow(new DataAccessResourceFailureException("offline"));
        assertThatThrownBy(() -> listener.consume(message())).isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(metrics.getMeters()).isEmpty();
    }
    @Test
    void malformedMessageNeverReachesTransaction() {
        assertThatThrownBy(() -> listener.consume(message(payload().put("schemaVersion", 2), EVENT_ID)))
                .isInstanceOf(TripPublicationRejectedException.class);
        verifyNoInteractions(transaction);
    }
}
