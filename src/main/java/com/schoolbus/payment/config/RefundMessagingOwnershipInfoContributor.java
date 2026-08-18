package com.schoolbus.payment.config;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class RefundMessagingOwnershipInfoContributor
        implements InfoContributor {

    static final String DETAIL_KEY = "refundMessagingOwner";

    private final Environment environment;

    public RefundMessagingOwnershipInfoContributor(
            Environment environment
    ) {
        this.environment = Objects.requireNonNull(
                environment,
                "environment must not be null"
        );
    }

    @Override
    public void contribute(Info.Builder builder) {
        boolean embedded = environment.getProperty(
                "school-bus.payment.refund-messaging.embedded",
                Boolean.class,
                true
        );
        builder.withDetail(
                DETAIL_KEY,
                embedded ? "core" : "disabled"
        );
    }
}
