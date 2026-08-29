package com.schoolbus.gateway.security;

import reactor.core.publisher.Mono;

public interface AccessTokenRevocationStore {

    Mono<Long> findRevokedBeforeEpochMilli(String subject);
}
