package com.schoolbus.iamservice.infrastructure.security.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "school-bus.security.admin-bootstrap",
        name = "enabled",
        havingValue = "true"
)
@EnableConfigurationProperties(AdminBootstrapProperties.class)
public class AdminRoleBootstrapRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            AdminRoleBootstrapRunner.class
    );

    private final AdminBootstrapProperties properties;
    private final AdminRoleProvisioningService provisioningService;

    public AdminRoleBootstrapRunner(
            AdminBootstrapProperties properties,
            AdminRoleProvisioningService provisioningService
    ) {
        this.properties = properties;
        this.provisioningService = provisioningService;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean created = provisioningService.provision(
                properties.studentNumber()
        );
        LOGGER.info(
                "Admin role bootstrap {} for configured account",
                created ? "created" : "already existed"
        );
    }
}
