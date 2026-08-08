package com.schoolbus.shared.api;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    TRIP_NOT_BOOKABLE(HttpStatus.CONFLICT, "trip is not bookable"),
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
