package com.schoolbus.gateway.security;

import com.schoolbus.gateway.config.TokenRevocationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;

@Component
@ConditionalOnProperty(
        prefix = "school-bus.gateway.token-revocation",
        name = "enabled",
        havingValue = "true"
)
public class AccessTokenRevocationGatewayFilter
        implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";
    private final ReactiveJwtDecoder jwtDecoder;
    private final AccessTokenRevocationStore revocationStore;
    private final boolean failClosed;

    public AccessTokenRevocationGatewayFilter(
            ReactiveJwtDecoder jwtDecoder,
            AccessTokenRevocationStore revocationStore,
            TokenRevocationProperties properties
    ) {
        this.jwtDecoder = Objects.requireNonNull(
                jwtDecoder,
                "jwtDecoder must not be null"
        );
        this.revocationStore = Objects.requireNonNull(
                revocationStore,
                "revocationStore must not be null"
        );
        this.failClosed = Objects.requireNonNull(
                properties,
                "properties must not be null"
        ).failClosed();
    }

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            GatewayFilterChain chain
    ) {
        String authorization = exchange.getRequest()
                .getHeaders()
                .getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null) {
            return chain.filter(exchange);
        }
        if (!authorization.regionMatches(
                true,
                0,
                BEARER_PREFIX,
                0,
                BEARER_PREFIX.length()
        )) {
            return chain.filter(exchange);
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            return unauthorized(exchange);
        }

        return jwtDecoder.decode(token)
                .flatMap(jwt -> checkRevocation(exchange, chain, jwt))
                .onErrorResume(
                        JwtException.class,
                        ignored -> unauthorized(exchange)
                );
    }

    private Mono<Void> checkRevocation(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            Jwt jwt
    ) {
        if (isLogoutRequest(exchange)) {
            return chain.filter(exchange);
        }
        if (jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            return unauthorized(exchange);
        }
        long issuedAtMillis = issuedAtMillis(jwt);
        Mono<Boolean> revoked = revocationStore
                .findRevokedBeforeEpochMilli(jwt.getSubject())
                .map(revokedBefore -> revokedBefore >= issuedAtMillis)
                .defaultIfEmpty(false)
                .onErrorResume(error -> failClosed
                        ? Mono.error(new RevocationCheckUnavailableException(error))
                        : Mono.just(false)
                );
        return revoked
                .flatMap(isRevoked -> isRevoked
                        ? unauthorized(exchange)
                        : chain.filter(exchange)
                )
                .onErrorResume(
                        RevocationCheckUnavailableException.class,
                        ignored -> unavailable(exchange)
                );
    }

    private static long issuedAtMillis(Jwt jwt) {
        Number preciseIssuedAt = jwt.getClaim("iat_ms");
        if (preciseIssuedAt != null) {
            return preciseIssuedAt.longValue();
        }
        Instant issuedAt = jwt.getIssuedAt();
        if (issuedAt == null) {
            throw new JwtException("access token is missing issued-at");
        }
        return issuedAt.toEpochMilli();
    }

    private static boolean isLogoutRequest(ServerWebExchange exchange) {
        return HttpMethod.POST.equals(exchange.getRequest().getMethod())
                && "/api/v1/auth/logout".equals(
                exchange.getRequest().getPath().value()
        );
    }

    private static Mono<Void> unauthorized(ServerWebExchange exchange) {
        return writeJson(
                exchange,
                HttpStatus.UNAUTHORIZED,
                "{\"code\":\"UNAUTHORIZED\",\"message\":\"invalid or revoked access token\"}"
        );
    }

    private static Mono<Void> unavailable(ServerWebExchange exchange) {
        return writeJson(
                exchange,
                HttpStatus.SERVICE_UNAVAILABLE,
                "{\"code\":\"TOKEN_REVOCATION_UNAVAILABLE\",\"message\":\"token revocation check unavailable\"}"
        );
    }

    private static Mono<Void> writeJson(
            ServerWebExchange exchange,
            HttpStatus status,
            String body
    ) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(
                MediaType.APPLICATION_JSON
        );
        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(
                Mono.just(buffer)
        );
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

    private static final class RevocationCheckUnavailableException
            extends RuntimeException {

        private RevocationCheckUnavailableException(Throwable cause) {
            super(cause);
        }
    }
}
