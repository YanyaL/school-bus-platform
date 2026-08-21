package com.schoolbus.cdcsync.messaging;

import com.schoolbus.cdcsync.event.CdcEvent;

public interface CdcEventPublisher {

    void publish(CdcEvent event);
}
