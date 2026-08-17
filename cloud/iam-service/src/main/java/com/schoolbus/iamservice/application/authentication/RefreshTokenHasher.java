package com.schoolbus.iamservice.application.authentication;

public interface RefreshTokenHasher {

    String hash(String rawRefreshToken);
}
