package com.schoolbus.paymentservice.application.refund;

public interface ConsumedEventCache {

    boolean contains(String consumerName, String eventId);
}
