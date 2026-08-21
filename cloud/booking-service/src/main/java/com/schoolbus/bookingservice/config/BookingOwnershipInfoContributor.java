package com.schoolbus.bookingservice.config;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

@Component
public class BookingOwnershipInfoContributor implements InfoContributor {

    static final String DETAIL_KEY = "bookingOwner";

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail(DETAIL_KEY, "booking");
    }
}
