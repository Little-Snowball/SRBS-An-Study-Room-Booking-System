package edu.campus.studyroom.common;

import org.springframework.http.HttpStatus;

/**
 * Unified business error codes. The frontend branches on these codes,
 * never on Chinese messages. HTTP mapping follows the implementation manual chapter 10.
 */
public enum ErrorCode {

    // 400
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    // 401
    AUTH_REQUIRED(HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED),
    // 403
    FORBIDDEN(HttpStatus.FORBIDDEN),
    PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN),
    // 404
    NOT_FOUND(HttpStatus.NOT_FOUND),
    // 405
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    // 409 time-slot / state / idempotency conflicts
    SLOT_CONFLICT(HttpStatus.CONFLICT),
    USER_TIME_CONFLICT(HttpStatus.CONFLICT),
    INVALID_STATE(HttpStatus.CONFLICT),
    APPEAL_EXISTS(HttpStatus.CONFLICT),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT),
    // 422 business-rule violations
    ACCOUNT_DISABLED(HttpStatus.UNPROCESSABLE_ENTITY),
    ACCOUNT_BLOCKED(HttpStatus.UNPROCESSABLE_ENTITY),
    CREDIT_INSUFFICIENT(HttpStatus.UNPROCESSABLE_ENTITY),
    OUTSIDE_OPEN_HOURS(HttpStatus.UNPROCESSABLE_ENTITY),
    INVALID_TIME_GRID(HttpStatus.UNPROCESSABLE_ENTITY),
    CANCEL_WINDOW_CLOSED(HttpStatus.UNPROCESSABLE_ENTITY),
    CHECKIN_WINDOW_CLOSED(HttpStatus.UNPROCESSABLE_ENTITY),
    SEAT_CURRENTLY_OCCUPIED(HttpStatus.UNPROCESSABLE_ENTITY),
    INVALID_CHECKIN_CODE(HttpStatus.UNPROCESSABLE_ENTITY),
    LEAVE_LIMIT_REACHED(HttpStatus.UNPROCESSABLE_ENTITY),
    APPEAL_EXPIRED(HttpStatus.UNPROCESSABLE_ENTITY),
    // 429
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    // 503
    BUSY_RETRY(HttpStatus.SERVICE_UNAVAILABLE),
    // 500
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus httpStatus;

    ErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
