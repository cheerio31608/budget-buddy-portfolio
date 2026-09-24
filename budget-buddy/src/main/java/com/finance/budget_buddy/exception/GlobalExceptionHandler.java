package com.finance.budget_buddy.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 비즈니스 로직 처리 중 발생하는 예외를 처리합니다.
     */
    @ExceptionHandler(BusinessException.class)
    protected ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        log.warn("Business request rejected [{}]", e.getErrorCode().getCode());
        ErrorCode errorCode = e.getErrorCode();
        ErrorResponse response = ErrorResponse.of(errorCode, e.getMessage());
        return new ResponseEntity<>(response, errorCode.getStatus());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse(ErrorCode.INVALID_INPUT_VALUE.getMessage());

        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(ErrorCode.INVALID_INPUT_VALUE, message));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            HandlerMethodValidationException.class,
            ConstraintViolationException.class
    })
    protected ResponseEntity<ErrorResponse> handleInvalidRequest(Exception exception) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(ErrorCode.INVALID_INPUT_VALUE,
                        "요청 값의 형식이나 필수 항목을 확인해 주세요."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    protected ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception) {
        ErrorCode errorCode = ErrorCode.METHOD_NOT_ALLOWED;
        return new ResponseEntity<>(ErrorResponse.of(errorCode), errorCode.getStatus());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    protected ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException exception) {
        ErrorCode errorCode = ErrorCode.RESOURCE_NOT_FOUND;
        return new ResponseEntity<>(ErrorResponse.of(errorCode), errorCode.getStatus());
    }

    @ExceptionHandler(BadCredentialsException.class)
    protected ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException exception) {
        ErrorCode errorCode = ErrorCode.LOGIN_FAILED;
        return new ResponseEntity<>(ErrorResponse.of(errorCode), errorCode.getStatus());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    protected ResponseEntity<ErrorResponse> handleFileSize(MaxUploadSizeExceededException exception) {
        ErrorCode errorCode = ErrorCode.CSV_FILE_TOO_LARGE;
        return new ResponseEntity<>(ErrorResponse.of(errorCode), errorCode.getStatus());
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    protected ResponseEntity<ErrorResponse> handleConcurrency(ConcurrencyFailureException exception) {
        return ResponseEntity.status(ErrorCode.CONCURRENT_UPDATE.getStatus())
                .body(ErrorResponse.of(ErrorCode.CONCURRENT_UPDATE));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    protected ResponseEntity<ErrorResponse> handleDataConflict(DataIntegrityViolationException exception) {
        // Database messages can contain row values; never return or log the cause.
        log.warn("Database constraint rejected a write [{}]", ErrorCode.DATA_CONFLICT.getCode());
        return ResponseEntity.status(ErrorCode.DATA_CONFLICT.getStatus())
                .body(ErrorResponse.of(ErrorCode.DATA_CONFLICT));
    }

    /** Unexpected errors retain type and code location, excluding payload-bearing messages. */
    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("Unhandled exception type={} location={}", e.getClass().getName(),
                e.getStackTrace().length == 0 ? "unknown" : e.getStackTrace()[0]);
        ErrorResponse response = ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR);
        return new ResponseEntity<>(response, ErrorCode.INTERNAL_SERVER_ERROR.getStatus());
    }
}
