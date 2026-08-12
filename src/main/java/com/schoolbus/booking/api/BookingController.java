package com.schoolbus.booking.api;

import com.schoolbus.booking.application.booking.BookingApplicationService;
import com.schoolbus.booking.application.booking.BookingQueryApplicationService;
import com.schoolbus.booking.application.booking.BookingSortOption;
import com.schoolbus.booking.application.booking.CreateBookingCommand;
import com.schoolbus.booking.application.booking.CreateBookingOutcome;
import com.schoolbus.booking.application.booking.ListMyBookingsQuery;
import com.schoolbus.booking.domain.order.BookingStatus;
import com.schoolbus.shared.api.ApiResponse;
import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;
import com.schoolbus.shared.api.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/bookings")
@Validated
@Profile("!test")
public class BookingController {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String IDEMPOTENCY_REPLAYED_HEADER = "Idempotency-Replayed";

    private final BookingApplicationService applicationService;
    private final BookingQueryApplicationService queryApplicationService;

    public BookingController(
            BookingApplicationService applicationService,
            BookingQueryApplicationService queryApplicationService
    ) {
        this.applicationService = Objects.requireNonNull(
                applicationService,
                "applicationService must not be null"
        );
        this.queryApplicationService = Objects.requireNonNull(
                queryApplicationService,
                "queryApplicationService must not be null"
        );
    }

    @GetMapping
    public ApiResponse<PageResponse<BookingSummaryResponse>> listMyBookings(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "page must not be negative")
            int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must not exceed 100")
            int size,
            @RequestParam(defaultValue = "createdAt,desc")
            String sort
    ) {
        ListMyBookingsQuery query = new ListMyBookingsQuery(
                Long.parseLong(jwt.getSubject()),
                parseStatus(status),
                page,
                size,
                BookingSortOption.parseCreatedAtAscending(sort)
        );
        List<BookingSummaryResponse> items = queryApplicationService
                .listMyBookings(query)
                .stream()
                .map(BookingSummaryResponse::from)
                .toList();
        long totalElements = queryApplicationService.countMyBookings(query);

        return ApiResponse.success(
                PageResponse.of(items, page, size, totalElements)
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

    private BookingStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return BookingStatus.valueOf(status.strip());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "status must be a valid booking status"
            );
        }
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
