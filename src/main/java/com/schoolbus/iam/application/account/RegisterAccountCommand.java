package com.schoolbus.iam.application.account;

public record RegisterAccountCommand(
        String studentNumber,
        String rawPassword
) {

    public RegisterAccountCommand {
        if (studentNumber == null ||
                studentNumber.isBlank()) {
            throw new IllegalArgumentException(
                    "studentNumber must not be blank"
            );
        }

        if (rawPassword == null ||
                rawPassword.isBlank()) {
            throw new IllegalArgumentException(
                    "rawPassword must not be blank"
            );
        }
    }
}
