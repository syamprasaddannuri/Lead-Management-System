package com.leadmanagement.lms.common;

import org.springframework.http.HttpStatus;

import java.util.Map;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final Map<String, Object> details;

    public ApiException(HttpStatus status, String message) {
        this(status, message, null);
    }

    public ApiException(HttpStatus status, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.details = details;
    }

    public HttpStatus getStatus() { return status; }
    public Map<String, Object> getDetails() { return details; }

    public static ApiException badRequest(String msg) { return new ApiException(HttpStatus.BAD_REQUEST, msg); }
    public static ApiException notFound(String msg) { return new ApiException(HttpStatus.NOT_FOUND, msg); }
    public static ApiException conflict(String msg) { return new ApiException(HttpStatus.CONFLICT, msg); }
    public static ApiException conflict(String msg, Map<String, Object> details) { return new ApiException(HttpStatus.CONFLICT, msg, details); }
    public static ApiException unauthorized(String msg) { return new ApiException(HttpStatus.UNAUTHORIZED, msg); }
}
