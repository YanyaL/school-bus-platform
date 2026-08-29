package com.schoolbus.transport.config;

import com.schoolbus.transport.api.admin.AdminRouteController;
import com.schoolbus.transport.api.admin.AdminVehicleController;
import com.schoolbus.transport.application.route.RouteManagementApplicationService;
import com.schoolbus.transport.application.vehicle.VehicleCreationTransaction;
import com.schoolbus.transport.application.vehicle.VehicleManagementApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddedTransportAdminConfigurationTest {

    private static final List<Class<?>> TRANSPORT_ADMIN_OWNED_TYPES = List.of(
            AdminVehicleController.class,
            AdminRouteController.class,
            VehicleManagementApplicationService.class,
            VehicleCreationTransaction.class,
            RouteManagementApplicationService.class
    );

    @Test
    void removesEveryCoreTransportAdminBeanWhenCommandServiceOwnsRuntime() {
        new ApplicationContextRunner()
                .withPropertyValues(
                        "spring.profiles.active=local",
                        "school-bus.transport.admin.embedded.enabled=false"
                )
                .withUserConfiguration(AllTransportAdminTypesImport.class)
                .run(context -> TRANSPORT_ADMIN_OWNED_TYPES.forEach(type ->
                        assertThat(context).doesNotHaveBean(type)
                ));
    }

    @Test
    void everyTransportAdminOwnedTypeUsesOwnershipCondition() {
        TRANSPORT_ADMIN_OWNED_TYPES.forEach(type -> assertThat(
                type.isAnnotationPresent(ConditionalOnEmbeddedTransportAdmin.class)
        ).as(type.getName() + " must be guarded").isTrue());
    }

    @Test
    void ownershipContributorReportsCoreAndDisabledModes() {
        InfoDetails core = contribute(new MockEnvironment());
        InfoDetails disabled = contribute(new MockEnvironment()
                .withProperty("school-bus.transport.admin.embedded.enabled", "false"));

        assertThat(core.value()).isEqualTo("core");
        assertThat(disabled.value()).isEqualTo("disabled");
    }

    private static InfoDetails contribute(MockEnvironment environment) {
        org.springframework.boot.actuate.info.Info.Builder builder =
                new org.springframework.boot.actuate.info.Info.Builder();
        new TransportAdminOwnershipInfoContributor(environment).contribute(builder);
        return new InfoDetails(builder.build().get(TransportAdminOwnershipInfoContributor.DETAIL_KEY));
    }

    private record InfoDetails(Object value) {
    }

    @Configuration(proxyBeanMethods = false)
    @Import({
            AdminVehicleController.class,
            AdminRouteController.class,
            VehicleManagementApplicationService.class,
            VehicleCreationTransaction.class,
            RouteManagementApplicationService.class
    })
    static class AllTransportAdminTypesImport {
    }
}
