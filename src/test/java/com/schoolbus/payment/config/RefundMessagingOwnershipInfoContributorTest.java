package com.schoolbus.payment.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class RefundMessagingOwnershipInfoContributorTest {

    @Test
    void reportsCoreOwnershipWhenEmbedded() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(
                "school-bus.payment.refund-messaging.embedded",
                "true"
        );
        Info.Builder builder = new Info.Builder();
        new RefundMessagingOwnershipInfoContributor(environment)
                .contribute(builder);
        Info info = builder.build();
        assertThat(info.getDetails())
                .containsEntry("refundMessagingOwner", "core");
    }

    @Test
    void reportsDisabledOwnershipWhenPaymentOwnsMessaging() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(
                "school-bus.payment.refund-messaging.embedded",
                "false"
        );
        Info.Builder builder = new Info.Builder();
        new RefundMessagingOwnershipInfoContributor(environment)
                .contribute(builder);
        Info info = builder.build();
        assertThat(info.getDetails())
                .containsEntry("refundMessagingOwner", "disabled");
    }
}
