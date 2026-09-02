package com.schoolbus.transportcommand.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;

import static org.assertj.core.api.Assertions.assertThat;

class TransportCommandOwnershipInfoContributorTest {

    @Test
    void reportsTransportCommandAsVehicleAndRouteAdminOwner() {
        Info.Builder builder = new Info.Builder();

        new TransportCommandOwnershipInfoContributor().contribute(builder);

        assertThat(builder.build().get("transportAdminOwner"))
                .isEqualTo("transport-command");
    }
}
