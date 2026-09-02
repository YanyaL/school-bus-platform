package com.schoolbus.shared.api;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException exception
    ) {
        return ResponseEntity
                .status(ErrorCode.FORBIDDEN.httpStatus())
                .body(ApiErrorResponse.of(ErrorCode.FORBIDDEN, List.of()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.errorCode();
        return ResponseEntity
                .status(errorCode.httpStatus())
                .body(ApiErrorResponse.of(exception));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception
    ) {
        List<FieldErrorDetail> details = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toFieldErrorDetail)
                .toList();
        return ResponseEntity
                .badRequest()
                .body(ApiErrorResponse.of(ErrorCode.VALIDATION_ERROR, details));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception
    ) {
        List<FieldErrorDetail> details = exception.getConstraintViolations()
                .stream()
                .map(violation -> new FieldErrorDetail(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()
                ))
                .toList();
        return ResponseEntity
                .badRequest()
                .body(ApiErrorResponse.of(ErrorCode.VALIDATION_ERROR, details));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleMalformedJson() {
        return ResponseEntity
                .badRequest()
                .body(ApiErrorResponse.of(ErrorCode.MALFORMED_JSON, List.of()));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingRequestHeader(
            MissingRequestHeaderException exception
    ) {
        List<FieldErrorDetail> details = List.of(
                new FieldErrorDetail(
                        exception.getHeaderName(),
                        "required request header is missing"
                )
        );
        return ResponseEntity
                .badRequest()
                .body(ApiErrorResponse.of(ErrorCode.VALIDATION_ERROR, details));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResourceFound(
            NoResourceFoundException exception
    ) {
        return ResponseEntity
                .status(ErrorCode.RESOURCE_NOT_FOUND.httpStatus())
                .body(ApiErrorResponse.of(ErrorCode.RESOURCE_NOT_FOUND, List.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(Exception exception) {
        log.error("Unhandled request exception", exception);
        return ResponseEntity
                .internalServerError()
                .body(ApiErrorResponse.of(ErrorCode.INTERNAL_ERROR, List.of()));
    }

    private FieldErrorDetail toFieldErrorDetail(FieldError fieldError) {
        String reason = fieldError.getDefaultMessage() == null
                ? "invalid"
                : fieldError.getDefaultMessage();
        return new FieldErrorDetail(fieldError.getField(), reason);
    }
}
