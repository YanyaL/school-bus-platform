package com.schoolbus.transportcommand.config;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

@Component
public class TransportCommandOwnershipInfoContributor
        implements InfoContributor {

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("transportAdminOwner", "transport-command");
    }
}
