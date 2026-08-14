package com.schoolbus.shared.web.ratelimit;

public interface TrafficGuard {

    TrafficPermit acquire(String resource);
}
