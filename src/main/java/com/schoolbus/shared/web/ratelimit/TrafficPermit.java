package com.schoolbus.shared.web.ratelimit;

@FunctionalInterface
public interface TrafficPermit extends AutoCloseable {

    TrafficPermit NO_OP = () -> {
    };

    @Override
    void close();
}
