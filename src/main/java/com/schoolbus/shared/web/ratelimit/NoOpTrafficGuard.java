package com.schoolbus.shared.web.ratelimit;

public class NoOpTrafficGuard implements TrafficGuard {

    @Override
    public TrafficPermit acquire(String resource) {
        return TrafficPermit.NO_OP;
    }
}
