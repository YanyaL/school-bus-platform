package com.schoolbus.shared.web.ratelimit;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;

public class SentinelTrafficGuard implements TrafficGuard {

    @Override
    public TrafficPermit acquire(String resource) {
        String checkedResource = requireText(resource);
        try {
            Entry entry = SphU.entry(checkedResource, EntryType.IN);
            return entry::exit;
        } catch (BlockException exception) {
            throw new RateLimitExceededException(checkedResource);
        }
    }

    private String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "resource must not be blank"
            );
        }
        return value.strip();
    }
}
