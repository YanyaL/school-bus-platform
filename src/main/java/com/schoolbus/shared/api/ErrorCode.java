package com.schoolbus.shared.api;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "请求参数校验失败"),
    MALFORMED_JSON(HttpStatus.BAD_REQUEST, "请求 JSON 无法解析"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "请先登录"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "没有权限执行此操作"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "请求的资源不存在"),
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
