package com.schoolbus.iamservice.infrastructure.security.bootstrap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminBootstrapPropertiesTest {

    @Test
    void shouldAllowDisabledBootstrapWithoutAccount() {
        assertThat(new AdminBootstrapProperties(false, null).studentNumber())
                .isEmpty();
    }

    @Test
    void shouldRequireAccountWhenBootstrapIsEnabled() {
        assertThatThrownBy(() -> new AdminBootstrapProperties(true, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("studentNumber");
    }
}
