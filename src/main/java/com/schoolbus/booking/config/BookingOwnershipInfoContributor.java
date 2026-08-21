package com.schoolbus.booking.config;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class BookingOwnershipInfoContributor implements InfoContributor {

    static final String DETAIL_KEY = "bookingOwner";

    private final Environment environment;

    public BookingOwnershipInfoContributor(Environment environment) {
        this.environment = Objects.requireNonNull(
                environment,
                "environment must not be null"
        );
    }

    @Override
    public void contribute(Info.Builder builder) {
        boolean embedded = environment.getProperty(
                "school-bus.booking.embedded.enabled",
                Boolean.class,
                true
        );
        builder.withDetail(DETAIL_KEY, embedded ? "core" : "disabled");
    }
}
