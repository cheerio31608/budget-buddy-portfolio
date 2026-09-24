package com.finance.budget_buddy.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", "Invalid input value."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C002", "Method not allowed."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C003", "Internal server error."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "C005", "Resource not found."),
    CONCURRENT_UPDATE(HttpStatus.CONFLICT, "C006", "Concurrent update. Retry the same request shortly."),
    DATA_CONFLICT(HttpStatus.CONFLICT, "C007", "Data conflicts with an existing record or constraint."),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U001", "User not found."),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "C004", "Category not found."),
    TRANSACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "T001", "Transaction not found."),
    INSUFFICIENT_BALANCE(HttpStatus.BAD_REQUEST, "T002", "Insufficient balance."),
    TRANSACTION_TYPE_MISMATCH(HttpStatus.BAD_REQUEST, "T003", "Transaction type does not match category type."),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "T004", "Idempotency key was already used for a different transaction."),
    BALANCE_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "T005", "Balance exceeds the supported amount range."),

    CSV_EMPTY_FILE(HttpStatus.BAD_REQUEST, "CSV001", "CSV file is empty."),
    CSV_INVALID_FORMAT(HttpStatus.BAD_REQUEST, "CSV002", "CSV format is invalid."),
    CSV_REQUIRED_COLUMN_MISSING(HttpStatus.BAD_REQUEST, "CSV003", "Required CSV column mapping is missing."),
    CSV_FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "CSV004", "CSV file is too large."),
    ANALYSIS_DATA_NOT_FOUND(HttpStatus.NOT_FOUND, "A001", "No data is available for analysis."),
    AI_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "A002", "Gemini API key is not configured."),
    AI_SERVICE_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "A003", "AI report service is temporarily unavailable."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "AUTH001", "Email or password is incorrect."),
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "AUTH004", "이미 등록된 이메일입니다."),
    REQUEST_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "LIMIT001", "요청 횟수를 초과했습니다. 잠시 후 다시 시도해 주세요."),
    AI_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "A004", "AI 생성 한도 또는 재생성 대기시간을 초과했습니다."),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "AUTH002", "Authentication is required."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "AUTH003", "Access is denied."),

    DEMO_DATA_ALREADY_EXISTS(HttpStatus.CONFLICT, "D001", "Demo data can only be added to an empty account.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
}
