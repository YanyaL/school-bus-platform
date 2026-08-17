package com.schoolbus.iamservice.application.authentication;

public record LogoutCommand(String sessionId) {

    public LogoutCommand {
        if (sessionId == null || sessionId.isBlank()) {
            throw new InvalidLoginSessionException();
        }
    }
}
