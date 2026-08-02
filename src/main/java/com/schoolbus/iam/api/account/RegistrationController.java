package com.schoolbus.iam.api.account;

import com.schoolbus.iam.application.account.RegisterAccountCommand;
import com.schoolbus.iam.application.account.RegistrationApplicationService;
import com.schoolbus.iam.domain.account.Account;
import com.schoolbus.shared.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/accounts")
@Profile("!test")
public class RegistrationController {

    private final RegistrationApplicationService service;

    public RegistrationController(
            RegistrationApplicationService service
    ) {
        this.service = Objects.requireNonNull(
                service,
                "service must not be null"
        );
    }

    @PostMapping
    public ResponseEntity<
            ApiResponse<RegisterAccountResponse>
    > register(
            @Valid
            @RequestBody RegisterAccountRequest request
    ) {
        RegisterAccountCommand command =
                new RegisterAccountCommand(
                        request.studentNumber(),
                        request.password()
                );

        Account account = service.register(command);

        RegisterAccountResponse response =
                RegisterAccountResponse.from(account);

        URI location = URI.create(
                "/api/v1/accounts/"
                        + account.userId().value()
        );

        return ResponseEntity
                .created(location)
                .body(ApiResponse.success(response));
    }
}
