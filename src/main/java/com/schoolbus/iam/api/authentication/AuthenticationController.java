package com.schoolbus.iam.api.authentication;

import com.schoolbus.iam.application.authentication.AuthenticationApplicationService;
import com.schoolbus.iam.application.authentication.AuthenticationResult;
import com.schoolbus.iam.application.authentication.LoginCommand;
import com.schoolbus.shared.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@RequestMapping("/api/v1/auth")
@Profile("!test")
public class AuthenticationController {

    private final AuthenticationApplicationService service;

    public AuthenticationController(
            AuthenticationApplicationService service
    ) {
        this.service = Objects.requireNonNull(
                service,
                "service must not be null"
        );
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(
            @Valid
            @RequestBody LoginRequest request
    ) {
        LoginCommand command = new LoginCommand(
                request.studentNumber(),
                request.password()
        );

        AuthenticationResult result = service.authenticate(command);

        return ApiResponse.success(
                LoginResponse.from(result)
        );
    }
}
