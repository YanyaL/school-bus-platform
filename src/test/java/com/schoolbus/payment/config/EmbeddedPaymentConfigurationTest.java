package com.schoolbus.payment.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolbus.payment.api.PaymentCallbackController;
import com.schoolbus.payment.api.PaymentCallbackVerifier;
import com.schoolbus.payment.application.PaymentConfirmationApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class EmbeddedPaymentConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withBean(
                            PaymentCallbackVerifier.class,
                            () -> mock(PaymentCallbackVerifier.class)
                    )
                    .withBean(
                            PaymentConfirmationApplicationService.class,
                            () -> mock(PaymentConfirmationApplicationService.class)
                    )
                    .withBean(ObjectMapper.class, ObjectMapper::new)
                    .withUserConfiguration(ControllerImport.class);

    @Test
    void keepsEmbeddedCallbackEnabledByDefault() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(PaymentCallbackController.class));
    }

    @Test
    void removesEmbeddedCallbackWhenCloudPaymentOwnsRoute() {
        contextRunner
                .withPropertyValues("school-bus.payment.embedded.enabled=false")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(PaymentCallbackController.class));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(PaymentCallbackController.class)
    static class ControllerImport {
    }
}
