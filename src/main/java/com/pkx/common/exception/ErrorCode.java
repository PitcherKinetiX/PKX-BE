package com.pkx.common.exception;

import lombok.Getter;

/**
 * Enumeration of common error codes used throughout the application.
 * Each error code has an associated HTTP status code and message.
 */
@Getter
public enum ErrorCode {
    // Common errors (1xxx)
    SUCCESS(1000, "Success"),
    INTERNAL_SERVER_ERROR(1001, "Internal server error"),
    INVALID_PARAMETER(1002, "Invalid parameter"),
    RESOURCE_NOT_FOUND(1003, "Resource not found"),

    // Authentication errors (2xxx)
    UNAUTHORIZED(2001, "Unauthorized"),
    INVALID_CREDENTIALS(2002, "Invalid credentials"),
    TOKEN_EXPIRED(2003, "Token expired"),
    TOKEN_INVALID(2004, "Token invalid"),
    REFRESH_TOKEN_EXPIRED(2005, "Refresh token expired"),
    INSUFFICIENT_PERMISSIONS(2006, "Insufficient permissions"),

    // User errors (3xxx)
    USER_NOT_FOUND(3001, "User not found"),
    USER_ALREADY_EXISTS(3002, "User already exists"),
    EMAIL_ALREADY_EXISTS(3003, "Email already exists"),
    USERNAME_ALREADY_EXISTS(3004, "Username already exists"),
    USER_DISABLED(3005, "User account is disabled"),

    // Validation errors (4xxx)
    VALIDATION_ERROR(4001, "Validation error"),
    INVALID_EMAIL_FORMAT(4002, "Invalid email format"),
    INVALID_PASSWORD_FORMAT(4003, "Invalid password format"),
    PASSWORD_TOO_SHORT(4004, "Password too short"),

    // File errors (5xxx)
    FILE_UPLOAD_ERROR(5001, "File upload error"),
    FILE_NOT_FOUND(5002, "File not found"),
    FILE_TOO_LARGE(5003, "File too large"),
    INVALID_FILE_TYPE(5004, "Invalid file type"),

    // Database errors (6xxx)
    DATABASE_ERROR(6001, "Database error"),
    DUPLICATE_KEY_ERROR(6002, "Duplicate key error"),

    // Analysis errors (5xxx continued)
    ANALYSIS_NOT_FOUND(5005, "Analysis not found"),
    ANALYSIS_NOT_COMPLETED(5006, "Analysis not completed"),
    ANALYSIS_IN_PROGRESS(5007, "Analysis in progress"),

    // External service errors (7xxx)
    EXTERNAL_SERVICE_ERROR(7001, "External service error"),
    REDIS_ERROR(7002, "Redis error"),
    GCS_ERROR(7003, "GCS error"),
    AI_SERVICE_ERROR(7004, "AI service error"),

    // AI model errors (8xxx)
    MODEL_NOT_READY(8001, "Personalized AI model is not ready. Please train your model first."),
    MODEL_TRAINING_IN_PROGRESS(8002, "Model training is already in progress");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
