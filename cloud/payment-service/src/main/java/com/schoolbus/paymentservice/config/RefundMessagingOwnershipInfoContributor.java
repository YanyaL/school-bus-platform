package com.schoolbus.paymentservice.config;

import com.schoolbus.paymentservice.application.PaymentMigrationProperties;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class RefundMessagingOwnershipInfoContributor
        implements InfoContributor {

    static final String DETAIL_KEY = "refundMessagingOwner";
    static final String WRITE_MODE_DETAIL_KEY = "paymentBookingWriteMode";

    private final PaymentMigrationProperties migrationProperties;

    public RefundMessagingOwnershipInfoContributor(
            PaymentMigrationProperties migrationProperties
    ) {
        this.migrationProperties = Objects.requireNonNull(
                migrationProperties
        );
    }

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail(DETAIL_KEY, "payment")
                .withDetail(
                        WRITE_MODE_DETAIL_KEY,
                        migrationProperties.bookingWriteMode().name()
                );
    }
}
