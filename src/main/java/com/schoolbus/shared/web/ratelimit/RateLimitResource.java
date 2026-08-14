package com.schoolbus.shared.web.ratelimit;

public final class RateLimitResource {

    public static final String LOGIN = "http:POST:/api/v1/auth/login";
    public static final String CREATE_BOOKING =
            "http:POST:/api/v1/bookings";
    public static final String PAYMENT_CALLBACK =
            "http:POST:/api/v1/payments/callback";

    private RateLimitResource() {
    }
}
