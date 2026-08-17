package com.schoolbus.iamservice.infrastructure.security.token;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Sha256RefreshTokenHasherTest {

    private final Sha256RefreshTokenHasher hasher =
            new Sha256RefreshTokenHasher();

    @Test
    void shouldProduceExpectedSha256Base64UrlHash() {
        String hash = hasher.hash("abc");

        assertThat(hash).isEqualTo(
                "ungWv48Bz-pBQUDeXa4iI7ADYaOWF3qctBD_YfIAFa0"
        );
    }

    @Test
    void shouldProduceSameHashForSameToken() {
        String firstHash = hasher.hash("refresh-token-001");
        String secondHash = hasher.hash("refresh-token-001");

        assertThat(firstHash).isEqualTo(secondHash);
    }

    @Test
    void shouldProduceDifferentHashesForDifferentTokens() {
        String firstHash = hasher.hash("refresh-token-001");
        String secondHash = hasher.hash("refresh-token-002");

        assertThat(firstHash).isNotEqualTo(secondHash);
    }

    @Test
    void shouldRejectBlankRawToken() {
        assertThatThrownBy(() -> hasher.hash(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
