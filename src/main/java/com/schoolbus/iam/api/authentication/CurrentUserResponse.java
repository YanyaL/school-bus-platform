package com.schoolbus.iam.api.authentication;

import java.util.List;
import java.util.Objects;

public record CurrentUserResponse(
        long userId,
        List<String> roles
) {

    public CurrentUserResponse {
        if (userId <= 0) {
            throw new IllegalArgumentException(
                    "userId must be positive"
            );
        }
        roles = List.copyOf(
                Objects.requireNonNull(
                        roles,
                        "roles must not be null"
                )
        );
    }
}
