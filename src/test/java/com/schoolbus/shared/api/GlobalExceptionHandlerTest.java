package com.schoolbus.shared.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler();

    @Test
    void shouldMapMissingHttpResourceToNotFound() {
        NoResourceFoundException exception =
                new NoResourceFoundException(
                        HttpMethod.GET,
                        "/missing"
                );

        ResponseEntity<ApiErrorResponse> response =
                handler.handleNoResourceFound(exception);

        assertThat(exception.getResourcePath()).isEqualTo("/missing");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.name());
    }
}
