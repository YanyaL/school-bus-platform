package com.schoolbus.iamservice.api.authentication;

import com.schoolbus.iamservice.application.authentication.AuthenticationApplicationService;
import com.schoolbus.iamservice.application.authentication.AuthenticationResult;
import com.schoolbus.iamservice.application.authentication.LoginCommand;
import com.schoolbus.iamservice.application.authentication.LogoutCommand;
import com.schoolbus.iamservice.application.authentication.RefreshAuthenticationCommand;
import com.schoolbus.iamservice.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/auth")
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

    @PostMapping("/refresh")
    public ApiResponse<RefreshTokenResponse> refresh(
            @Valid
            @RequestBody RefreshTokenRequest request
    ) {
        AuthenticationResult result = service.refresh(
                new RefreshAuthenticationCommand(
                        request.refreshToken()
                )
        );

        return ApiResponse.success(
                RefreshTokenResponse.from(result)
        );
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @AuthenticationPrincipal Jwt jwt
    ) {
        service.logout(
                new LogoutCommand(
                        Long.parseLong(jwt.getSubject())
                )
        );
        return ApiResponse.success(null);
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> currentUser(
            @AuthenticationPrincipal Jwt jwt
    ) {
        long userId = Long.parseLong(jwt.getSubject());
        List<String> roles = jwt.getClaimAsStringList("roles");

        return ApiResponse.success(
                CurrentUserResponse.of(
                        userId,
                        roles == null ? List.of() : roles
                )
        );
    }
}
