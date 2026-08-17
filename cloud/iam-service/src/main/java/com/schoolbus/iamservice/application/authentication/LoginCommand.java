package com.schoolbus.iamservice.application.authentication;

public record LoginCommand(
        String studentNumber,
        String rawPassword
) {

    public LoginCommand {
        if (studentNumber == null || studentNumber.isBlank()) {
            throw new IllegalArgumentException(
                    "studentNumber must not be blank"
            );
        }

        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException(
                    "rawPassword must not be blank"
            );
        }
    }
}
