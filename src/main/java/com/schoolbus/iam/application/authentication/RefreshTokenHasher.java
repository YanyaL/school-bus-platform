package com.schoolbus.iam.application.authentication;

public interface RefreshTokenHasher {

    String hash(String rawRefreshToken);
}
