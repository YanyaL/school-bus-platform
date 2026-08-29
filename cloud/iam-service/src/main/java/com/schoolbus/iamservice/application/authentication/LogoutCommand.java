package com.schoolbus.iamservice.application.authentication;

public record LogoutCommand(long userId) {

    public LogoutCommand {
        if (userId <= 0) {
            throw new InvalidLoginSessionException();
        }
    }
}
