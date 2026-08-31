package com.schoolbus.bookingservice.infrastructure.messaging.trippublication;

import com.schoolbus.bookingservice.application.trippublication.TripPublicationRejectedException;
import com.schoolbus.bookingservice.trippublication.PublicationFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static com.schoolbus.bookingservice.trippublication.PublicationFixtures.*;

class TripPublicationMessageDecoderTest {
    private final TripPublicationMessageDecoder decoder = new TripPublicationMessageDecoder(JSON);

    @Test
    void consumesProducerContractWithoutLosingLargeIdPrecision() {
        var event = decoder.decode(message());
        assertThat(event.snapshot().tripId()).isEqualTo(9007199254740993L);
        assertThat(event.snapshot().seatNumbers()).containsExactly("1", "2");
        assertThat(event.snapshot().price().toPlainString()).isEqualTo("5.00");
        assertThatThrownBy(() -> event.snapshot().seatNumbers().add("3")).isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "01", "9223372036854775808", " 123", "1e3"})
    void rejectsInvalidIdStrings(String id) {
        assertThatThrownBy(() -> decoder.decode(message(payload().put("tripId", id), EVENT_ID)))
                .isInstanceOf(TripPublicationRejectedException.class);
    }

    @Test
    void refusesNumericSnowflakeAndCoercedSchemaOrVersion() {
        rejects(payload().put("tripId", 9007199254740993L));
        rejects(payload().put("schemaVersion", "1"));
        rejects(payload().put("schemaVersion", 2));
        rejects(payload().put("tripVersion", 1.5));
        rejects(payload().put("tripVersion", 0));
    }

    @Test
    void rejectsSeatCountMismatchDuplicatesAndPriceCoercion() {
        rejects(payload().put("totalSeats", 3));
        var duplicate = payload(); duplicate.withArray("seatNumbers").set(1, JSON.getNodeFactory().textNode("1"));
        rejects(duplicate);
        rejects(payload().put("price", 5));
        rejects(payload().put("price", "5.001"));
        rejects(payload().put("bookingDeadline", "2026-08-31T00:00:00Z"));
    }

    @Test
    void rejectsMissingUnknownFieldsDuplicateJsonKeysTrailingDataAndOversizedBodies() {
        var missing = payload(); missing.remove("tripId"); rejects(missing);
        rejects(payload().put("futureField", "unsupported"));
        for (String body : new String[]{payload() + " {}", payload().toString().replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1")}) {
            assertThatThrownBy(() -> decoder.decode(message(body.getBytes(java.nio.charset.StandardCharsets.UTF_8), EVENT_ID)))
                    .isInstanceOf(TripPublicationRejectedException.class);
        }
        assertThatThrownBy(() -> decoder.decode(message(new byte[65537], EVENT_ID))).isInstanceOf(TripPublicationRejectedException.class);
    }

    @Test
    void rejectsMissingOrConflictingEnvelopeIdentityAndWrongType() {
        var wrongId = message(); wrongId.getMessageProperties().setHeader("eventId", UUID.randomUUID().toString());
        assertThatThrownBy(() -> decoder.decode(wrongId)).isInstanceOf(TripPublicationRejectedException.class);
        var wrongType = message(); wrongType.getMessageProperties().setType("PaymentSucceeded");
        assertThatThrownBy(() -> decoder.decode(wrongType)).isInstanceOf(TripPublicationRejectedException.class);
        var wrongSchema = message(); wrongSchema.getMessageProperties().setHeader("schemaVersion", 2);
        assertThatThrownBy(() -> decoder.decode(wrongSchema)).isInstanceOf(TripPublicationRejectedException.class);
        assertThatThrownBy(() -> decoder.decode(message(payload(), null))).isInstanceOf(TripPublicationRejectedException.class);
    }

    @Test
    void cosmeticJsonChangesDoNotChangeCanonicalSnapshot() {
        var first = decoder.decode(message()).snapshot();
        var reordered = JSON.createObjectNode();
        var fields = new java.util.ArrayList<String>(); payload().fieldNames().forEachRemaining(fields::add);
        java.util.Collections.reverse(fields);
        for (String name : fields) reordered.set(name, payload().get(name));
        assertThat(decoder.decode(message(reordered, EVENT_ID)).snapshot().canonicalJson())
                .isEqualTo(first.canonicalJson());
        var prettyPrinter = JSON.copy().enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);
        assertThat(new TripPublicationMessageDecoder(prettyPrinter).decode(message()).snapshot().canonicalJson())
                .isEqualTo(first.canonicalJson());
    }

    private void rejects(com.fasterxml.jackson.databind.node.ObjectNode body) {
        assertThatThrownBy(() -> decoder.decode(PublicationFixtures.message(body, EVENT_ID))).isInstanceOf(TripPublicationRejectedException.class);
    }
}
