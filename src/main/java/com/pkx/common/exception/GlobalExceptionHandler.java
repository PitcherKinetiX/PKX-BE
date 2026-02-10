package com.pkx.common.exception;

import com.pkx.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for REST controllers.
 * Catches exceptions and returns standardized error responses.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle custom business exceptions.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(BusinessException ex) {
        log.error("Business exception: code={}, message={}", ex.getCode(), ex.getCompleteMessage(), ex);

        HttpStatus status = determineHttpStatus(ex.getErrorCode());
        ApiResponse<Object> response = ApiResponse.error(ex.getCode(), ex.getCompleteMessage());

        return ResponseEntity.status(status).body(response);
    }

    /**
     * Handle validation errors from @Valid annotation.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationException(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        log.warn("Validation error: {}", errors);

        ApiResponse<Map<String, String>> response = ApiResponse.error(
                ErrorCode.VALIDATION_ERROR.getCode(),
                ErrorCode.VALIDATION_ERROR.getMessage(),
                errors
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handle authentication exceptions.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Object>> handleAuthenticationException(AuthenticationException ex) {
        log.error("Authentication error: {}", ex.getMessage(), ex);

        ApiResponse<Object> response;
        if (ex instanceof BadCredentialsException) {
            response = ApiResponse.error(
                    ErrorCode.INVALID_CREDENTIALS.getCode(),
                    ErrorCode.INVALID_CREDENTIALS.getMessage()
            );
        } else {
            response = ApiResponse.error(
                    ErrorCode.UNAUTHORIZED.getCode(),
                    ErrorCode.UNAUTHORIZED.getMessage()
            );
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    /**
     * Handle access denied exceptions.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDeniedException(AccessDeniedException ex) {
        log.error("Access denied: {}", ex.getMessage(), ex);

        ApiResponse<Object> response = ApiResponse.error(
                ErrorCode.INSUFFICIENT_PERMISSIONS.getCode(),
                ErrorCode.INSUFFICIENT_PERMISSIONS.getMessage()
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    /**
     * Handle method argument type mismatch exceptions.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleTypeMismatchException(
            MethodArgumentTypeMismatchException ex) {

        log.warn("Type mismatch error: parameter={}, value={}, requiredType={}",
                ex.getName(), ex.getValue(), ex.getRequiredType());

        String message = String.format("Invalid value '%s' for parameter '%s'",
                ex.getValue(), ex.getName());

        ApiResponse<Object> response = ApiResponse.error(
                ErrorCode.INVALID_PARAMETER.getCode(),
                message
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handle all other uncaught exceptions.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGenericException(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);

        ApiResponse<Object> response = ApiResponse.error(
                ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                ErrorCode.INTERNAL_SERVER_ERROR.getMessage()
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * Determine HTTP status based on error code.
     */
    private HttpStatus determineHttpStatus(ErrorCode errorCode) {
        return switch (errorCode.getCode() / 1000) {
            case 2 -> HttpStatus.UNAUTHORIZED;  // 2xxx: Authentication errors
            case 3 -> HttpStatus.BAD_REQUEST;   // 3xxx: User errors
            case 4 -> HttpStatus.BAD_REQUEST;   // 4xxx: Validation errors
            case 5 -> HttpStatus.BAD_REQUEST;   // 5xxx: File errors
            case 6 -> HttpStatus.INTERNAL_SERVER_ERROR; // 6xxx: Database errors
            case 7 -> HttpStatus.SERVICE_UNAVAILABLE;   // 7xxx: External service errors
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
