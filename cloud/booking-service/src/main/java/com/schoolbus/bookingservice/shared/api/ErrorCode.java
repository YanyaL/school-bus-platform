package com.schoolbus.bookingservice.shared.api;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    RATE_LIMITED(
            HttpStatus.TOO_MANY_REQUESTS,
            "too many requests; please retry later"
    ),
    TRIP_NOT_BOOKABLE(HttpStatus.CONFLICT, "trip is not bookable"),
    TRIP_INVENTORY_NOT_READY(
            HttpStatus.CONFLICT,
            "trip inventory is not ready"
    ),
    BOOKING_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "active booking already exists"
    ),
    SEAT_ALREADY_RESERVED(
            HttpStatus.CONFLICT,
            "seat is already reserved"
    ),
    SEAT_INVENTORY_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "seat inventory does not exist"
    ),
    BOOKING_CONCURRENCY_CONFLICT(
            HttpStatus.CONFLICT,
            "booking could not be completed because of concurrent updates"
    ),
    BOOKING_REQUEST_CONFLICT(
            HttpStatus.CONFLICT,
            "requestNumber has already been used for another booking"
    ),
    BOOKING_NOT_CANCELLABLE(
            HttpStatus.CONFLICT,
            "booking cannot be cancelled in its current status"
    ),
    PAYMENT_BOOKING_NOT_FOUND(HttpStatus.NOT_FOUND, "booking does not exist"),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.CONFLICT, "payment amount does not match booking amount"),
    PAYMENT_REQUEST_CONFLICT(HttpStatus.CONFLICT, "payment callback idempotency conflict"),
    PAYMENT_CONCURRENCY_CONFLICT(HttpStatus.CONFLICT, "payment confirmation conflicted with another update"),
    INVALID_PAYMENT_SIGNATURE(HttpStatus.UNAUTHORIZED, "invalid payment callback signature"),
    MALFORMED_PAYMENT_CALLBACK(HttpStatus.BAD_REQUEST, "payment callback body is invalid"),
    INVALID_LOGIN_SESSION(HttpStatus.UNAUTHORIZED, "invalid login session"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "invalid or expired refresh token"),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "请求参数校验失败"),
    MALFORMED_JSON(HttpStatus.BAD_REQUEST, "请求 JSON 无法解析"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "请先登录"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "学号或密码错误"),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "账户已被禁用"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "没有权限执行此操作"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "请求的资源不存在"),
    DUPLICATE_STUDENT_NUMBER(HttpStatus.CONFLICT, "学号已被注册"),
    VEHICLE_NOT_FOUND(HttpStatus.NOT_FOUND, "vehicle does not exist"),
    VEHICLE_NUMBER_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "vehicle number already exists"
    ),
    LICENSE_PLATE_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "license plate already exists"
    ),
    VEHICLE_STATUS_CONFLICT(
            HttpStatus.CONFLICT,
            "vehicle status cannot be changed"
    ),
    ROUTE_NOT_FOUND(HttpStatus.NOT_FOUND, "route does not exist"),
    ROUTE_CODE_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "route code already exists"
    ),
    ROUTE_STATUS_CONFLICT(
            HttpStatus.CONFLICT,
            "route status cannot be changed"
    ),
    INVALID_ROUTE_DIRECTION(
            HttpStatus.BAD_REQUEST,
            "departure and arrival campus must differ"
    ),
    INVALID_ROUTE_DEFINITION(
            HttpStatus.BAD_REQUEST,
            "route definition is invalid"
    ),
    TRIP_NOT_FOUND(HttpStatus.NOT_FOUND, "trip does not exist"),
    INVALID_TRIP_SCHEDULE(
            HttpStatus.BAD_REQUEST,
            "trip schedule is invalid"
    ),
    VEHICLE_NOT_AVAILABLE_FOR_TRIP(
            HttpStatus.CONFLICT,
            "vehicle is not available for trip scheduling"
    ),
    ROUTE_NOT_AVAILABLE_FOR_TRIP(
            HttpStatus.CONFLICT,
            "route is not available for trip scheduling"
    ),
    VEHICLE_SCHEDULE_CONFLICT(
            HttpStatus.CONFLICT,
            "vehicle already has an overlapping trip"
    ),
    TRIP_NOT_PUBLISHABLE(
            HttpStatus.CONFLICT,
            "trip cannot be published"
    ),
    TRIP_SEAT_TEMPLATE_INVALID(
            HttpStatus.CONFLICT,
            "vehicle seat template is missing or inconsistent"
    ),
    TRIP_NOT_CANCELLABLE(
            HttpStatus.CONFLICT,
            "trip cannot be cancelled in its current status"
    ),
    TRIP_HAS_ACTIVE_BOOKINGS(
            HttpStatus.CONFLICT,
            "trip has active bookings and requires coordinated cancellation"
    ),
    VERSION_CONFLICT(HttpStatus.CONFLICT, "资源已被其他请求修改"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "系统暂时无法处理请求");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
