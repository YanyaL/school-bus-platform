package com.schoolbus.booking.api;

import com.schoolbus.booking.application.booking.BookingApplicationService;
import com.schoolbus.booking.application.booking.CreateBookingCommand;
import com.schoolbus.booking.application.booking.CreateBookingOutcome;
import com.schoolbus.shared.api.ApiResponse;
import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/bookings")
@Profile("!test")
public class BookingController {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String IDEMPOTENCY_REPLAYED_HEADER = "Idempotency-Replayed";

    private final BookingApplicationService applicationService;

    public BookingController(
            BookingApplicationService applicationService
    ) {
        this.applicationService = Objects.requireNonNull(
                applicationService,
                "applicationService must not be null"
        );
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CreateBookingResponse>> createBooking(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            @Valid @RequestBody CreateBookingRequest request
    ) {
        validateIdempotencyKey(idempotencyKey);

        CreateBookingCommand command = new CreateBookingCommand(
                Long.parseLong(jwt.getSubject()),
                request.tripId(),
                request.seatNumber(),
                idempotencyKey
        );
        CreateBookingOutcome outcome = applicationService.createBookingOutcome(
                command
        );
        CreateBookingResponse response = CreateBookingResponse.from(
                outcome.result()
        );
        URI location = URI.create(
                "/api/v1/bookings/" + outcome.result().bookingNumber()
        );

        return ResponseEntity
                .created(location)
                .header(
                        IDEMPOTENCY_REPLAYED_HEADER,
                        Boolean.toString(outcome.idempotencyReplayed())
                )
                .body(ApiResponse.success(response));
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        String validatedKey = Objects.requireNonNull(
                idempotencyKey,
                "idempotencyKey must not be null"
        ).strip();
        if (validatedKey.isEmpty() || validatedKey.length() > 64) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Idempotency-Key length must be between 1 and 64"
            );
        }
    }
}
