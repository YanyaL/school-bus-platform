package com.schoolbus.gateway.security;

import com.schoolbus.gateway.config.TokenRevocationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessTokenRevocationGatewayFilterTest {

    private static final long ISSUED_AT_MILLIS = 1_787_900_000_123L;

    @Mock
    private ReactiveJwtDecoder jwtDecoder;

    @Mock
    private AccessTokenRevocationStore revocationStore;

    @Mock
    private GatewayFilterChain chain;

    private AccessTokenRevocationGatewayFilter filter;

    @BeforeEach
    void setUp() {
        filter = new AccessTokenRevocationGatewayFilter(
                jwtDecoder,
                revocationStore,
                new TokenRevocationProperties(
                        true,
                        true,
                        "schoolbus:auth:revoked-before:"
                )
        );
    }

    @Test
    void shouldPassRequestsWithoutBearerTokenToExistingRouteSecurity() {
        MockServerWebExchange exchange = exchange(null);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
        verify(jwtDecoder, never()).decode("token");
    }

    @Test
    void shouldRejectTokenIssuedAtOrBeforeRevocationWatermark() {
        MockServerWebExchange exchange = exchange("Bearer token");
        when(jwtDecoder.decode("token")).thenReturn(Mono.just(jwt()));
        when(revocationStore.findRevokedBeforeEpochMilli("1000001"))
                .thenReturn(Mono.just(ISSUED_AT_MILLIS));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(exchange);
    }

    @Test
    void shouldVerifyButAllowRepeatedLogoutToRemainIdempotent() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .build()
        );
        when(jwtDecoder.decode("token")).thenReturn(Mono.just(jwt()));
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
        verify(revocationStore, never())
                .findRevokedBeforeEpochMilli("1000001");
    }

    @Test
    void shouldTreatBearerSchemeCaseInsensitively() {
        MockServerWebExchange exchange = exchange("bearer token");
        when(jwtDecoder.decode("token")).thenReturn(Mono.just(jwt()));
        when(revocationStore.findRevokedBeforeEpochMilli("1000001"))
                .thenReturn(Mono.just(ISSUED_AT_MILLIS));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldAllowTokenIssuedAfterRevocationWatermark() {
        MockServerWebExchange exchange = exchange("Bearer token");
        when(jwtDecoder.decode("token")).thenReturn(Mono.just(jwt()));
        when(revocationStore.findRevokedBeforeEpochMilli("1000001"))
                .thenReturn(Mono.just(ISSUED_AT_MILLIS - 1));
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    void shouldRejectInvalidSignatureBeforeReadingRedis() {
        MockServerWebExchange exchange = exchange("Bearer invalid");
        when(jwtDecoder.decode("invalid"))
                .thenReturn(Mono.error(new JwtException("invalid")));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(exchange);
    }

    @Test
    void shouldFailClosedWhenRedisCannotConfirmRevocationState() {
        MockServerWebExchange exchange = exchange("Bearer token");
        when(jwtDecoder.decode("token")).thenReturn(Mono.just(jwt()));
        when(revocationStore.findRevokedBeforeEpochMilli("1000001"))
                .thenReturn(Mono.error(new IllegalStateException("redis down")));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        verify(chain, never()).filter(exchange);
    }

    private static MockServerWebExchange exchange(String authorization) {
        MockServerHttpRequest.BaseBuilder<?> request =
                MockServerHttpRequest.get("/api/v1/trips");
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return MockServerWebExchange.from(request.build());
    }

    private static Jwt jwt() {
        Instant issuedAt = Instant.ofEpochMilli(ISSUED_AT_MILLIS);
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("1000001")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(900))
                .audience(List.of("school-bus-api"))
                .claim("iat_ms", ISSUED_AT_MILLIS)
                .build();
    }
}
