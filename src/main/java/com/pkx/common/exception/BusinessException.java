package com.pkx.common.exception;

import lombok.Getter;

/**
 * Custom business exception that wraps error codes and messages.
 * This exception is used throughout the application to signal business logic errors.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String detailMessage;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.detailMessage = null;
    }

    public BusinessException(ErrorCode errorCode, String detailMessage) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.detailMessage = detailMessage;
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.detailMessage = null;
    }

    public BusinessException(ErrorCode errorCode, String detailMessage, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.detailMessage = detailMessage;
    }

    /**
     * Get the error code value.
     */
    public int getCode() {
        return errorCode.getCode();
    }

    /**
     * Get the complete error message including detail if present.
     */
    public String getCompleteMessage() {
        if (detailMessage != null && !detailMessage.isEmpty()) {
            return errorCode.getMessage() + ": " + detailMessage;
        }
        return errorCode.getMessage();
    }
}
