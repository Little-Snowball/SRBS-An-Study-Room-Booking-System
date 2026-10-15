package edu.campus.studyroom.common;

import java.io.Serial;

/**
 * Business rejection. Carries a fixed ErrorCode so controllers can map
 * it to the unified response envelope with the documented HTTP status.
 */
public class BusinessException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final ErrorCode code;
    private final Object errorDetails;

    public BusinessException(ErrorCode code, String message) {
        this(code, message, null);
    }

    public BusinessException(ErrorCode code, String message, Object errorDetails) {
        super(message);
        this.code = code;
        this.errorDetails = errorDetails;
    }

    public ErrorCode code() {
        return code;
    }

    public Object errorDetails() {
        return errorDetails;
    }

    public static BusinessException validation(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(ErrorCode.NOT_FOUND, message);
    }

    public static BusinessException invalidState(String message) {
        return new BusinessException(ErrorCode.INVALID_STATE, message);
    }

    public static BusinessException internal(String message) {
        return new BusinessException(ErrorCode.INTERNAL_ERROR, message);
    }
}
