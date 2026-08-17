package com.schoolbus.iamservice.domain.identity;

public record UserId(long value) {

    public UserId {
        if (value <= 0) {
            throw new IllegalArgumentException(
                    "userId must be positive"
            );
        }
    }

    public static UserId of(long value) {
        return new UserId(value);
    }
}
