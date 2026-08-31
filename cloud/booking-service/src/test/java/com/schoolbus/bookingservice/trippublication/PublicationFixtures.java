package com.schoolbus.bookingservice.trippublication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.schoolbus.bookingservice.application.trippublication.*;
import com.schoolbus.bookingservice.infrastructure.messaging.trippublication.TripPublicationMessageDecoder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import java.io.IOException;
import java.util.UUID;

public final class PublicationFixtures {
    public static final String EVENT_ID = "22222222-2222-4222-8222-222222222222";
    public static final ObjectMapper JSON = new ObjectMapper();
    private PublicationFixtures() { }
    public static ObjectNode payload() {
        try (var stream = PublicationFixtures.class.getResourceAsStream("/contracts/trip-published-v1.json")) {
            return (ObjectNode) JSON.readTree(stream);
        } catch (IOException e) { throw new IllegalStateException(e); }
    }
    public static Message message() { return message(payload(), EVENT_ID); }
    public static Message message(ObjectNode payload, String eventId) {
        return message(payload.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8), eventId);
    }
    public static Message message(byte[] body, String eventId) {
        MessageProperties props = new MessageProperties();
        props.setMessageId(eventId);
        props.setType("TripPublished");
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setDeliveryMode(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
        return new Message(body, props);
    }
    public static TripPublicationEnvelope event() { return new TripPublicationMessageDecoder(JSON).decode(message()); }
    public static TripPublicationEnvelope version(long version) {
        return new TripPublicationMessageDecoder(JSON).decode(message(payload().put("tripVersion", version), UUID.randomUUID().toString()));
    }
}
