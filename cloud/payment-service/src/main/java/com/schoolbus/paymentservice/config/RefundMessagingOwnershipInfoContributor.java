package com.schoolbus.paymentservice.config;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

@Component
public class RefundMessagingOwnershipInfoContributor
        implements InfoContributor {

    static final String DETAIL_KEY = "refundMessagingOwner";

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail(DETAIL_KEY, "payment");
    }
}
