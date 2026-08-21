package com.schoolbus.shared.application.messaging;

public interface ConsumedEventCache {

    boolean contains(String consumerName, String eventId);
}
