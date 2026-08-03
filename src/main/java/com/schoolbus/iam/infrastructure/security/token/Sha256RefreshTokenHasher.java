package com.schoolbus.iam.infrastructure.security.token;

import com.schoolbus.iam.application.authentication.RefreshTokenHasher;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Component
public class Sha256RefreshTokenHasher
        implements RefreshTokenHasher {

    private static final String HASH_ALGORITHM = "SHA-256";

    @Override
    public String hash(String rawRefreshToken) {
        if (rawRefreshToken == null
                || rawRefreshToken.isBlank()) {
            throw new IllegalArgumentException(
                    "rawRefreshToken must not be blank"
            );
        }

        try {
            MessageDigest messageDigest =
                    MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] digest = messageDigest.digest(
                    rawRefreshToken.getBytes(StandardCharsets.UTF_8)
            );
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is not available",
                    exception
            );
        }
    }
}
