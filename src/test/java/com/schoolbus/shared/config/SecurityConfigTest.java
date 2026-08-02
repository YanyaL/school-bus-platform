package com.schoolbus.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    @Test
    void shouldConvertJwtRolesToSpringAuthorities() {
        Jwt jwt = Jwt.withTokenValue("header.payload.signature")
                .header("alg", "RS256")
                .subject("1000001")
                .claim("roles", List.of("STUDENT"))
                .build();
        JwtAuthenticationConverter converter =
                new SecurityConfig().jwtAuthenticationConverter();

        assertThat(converter.convert(jwt))
                .isNotNull()
                .satisfies(authentication -> {
                    assertThat(authentication.getName())
                            .isEqualTo("1000001");
                    assertThat(authentication.getAuthorities())
                            .containsExactly(
                                    new SimpleGrantedAuthority(
                                            "ROLE_STUDENT"
                                    )
                            );
                });
    }
}
