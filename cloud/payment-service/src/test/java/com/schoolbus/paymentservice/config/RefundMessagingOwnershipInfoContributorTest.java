package com.schoolbus.paymentservice.config;

import com.schoolbus.paymentservice.application.PaymentBookingWriteMode;
import com.schoolbus.paymentservice.application.PaymentMigrationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;

import static org.assertj.core.api.Assertions.assertThat;

class RefundMessagingOwnershipInfoContributorTest {

    @Test
    void exposesOwnershipAndEventWriteMode() {
        RefundMessagingOwnershipInfoContributor contributor =
                new RefundMessagingOwnershipInfoContributor(
                        new PaymentMigrationProperties(
                                PaymentBookingWriteMode.EVENT
                        )
                );
        Info.Builder builder = new Info.Builder();

        contributor.contribute(builder);

        assertThat(builder.build().getDetails())
                .containsEntry(
                        RefundMessagingOwnershipInfoContributor.DETAIL_KEY,
                        "payment"
                )
                .containsEntry(
                        RefundMessagingOwnershipInfoContributor.WRITE_MODE_DETAIL_KEY,
                        "EVENT"
                );
    }
}
