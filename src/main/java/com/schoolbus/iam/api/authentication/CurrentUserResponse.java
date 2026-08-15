package com.schoolbus.iam.api.authentication;

import com.schoolbus.shared.api.HttpResourceId;

import java.util.List;
import java.util.Objects;

public record CurrentUserResponse(
        String userId,
        List<String> roles
) {

    public CurrentUserResponse {
        Objects.requireNonNull(userId, "userId must not be null");
        HttpResourceId.parse(userId, "userId");
        roles = List.copyOf(
                Objects.requireNonNull(
                        roles,
                        "roles must not be null"
                )
        );
    }

    public static CurrentUserResponse of(long userId, List<String> roles) {
        return new CurrentUserResponse(
                HttpResourceId.format(userId),
                roles
        );
    }
}
