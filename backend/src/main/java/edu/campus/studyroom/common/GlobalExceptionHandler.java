package edu.campus.studyroom.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global exception translation. Database errors surface only as
 * INTERNAL_ERROR / BUSY_RETRY; SQL, stacks and passwords never leak.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorEnvelope> handleBusiness(BusinessException ex) {
        if (ex.code() == ErrorCode.INTERNAL_ERROR) {
            log.error("business internal error code={} msg={}", ex.code(), ex.getMessage());
        } else {
            log.info("business reject code={} msg={}", ex.code(), ex.getMessage());
        }
        return ResponseEntity.status(ex.code().httpStatus())
                .body(ErrorEnvelope.of(ex.code(), ex.getMessage(), ex.errorDetails()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorEnvelope> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> details = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            details.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
                .body(ErrorEnvelope.of(ErrorCode.VALIDATION_ERROR, "参数校验失败", details));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingRequestHeaderException.class, IllegalArgumentException.class})
    public ResponseEntity<ErrorEnvelope> handleBadRequest(Exception ex) {
        String message = ex instanceof MissingRequestHeaderException
                ? "缺少必需请求头 " + ((MissingRequestHeaderException) ex).getHeaderName()
                : "请求格式不合法";
        return ResponseEntity.badRequest()
                .body(ErrorEnvelope.of(ErrorCode.VALIDATION_ERROR, message));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorEnvelope> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorEnvelope.of(ErrorCode.FORBIDDEN, "无权限执行该操作"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorEnvelope> handleUnknown(Exception ex, HttpServletRequest request) {
        log.error("unhandled error path={}", request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorEnvelope.of(ErrorCode.INTERNAL_ERROR, "服务内部错误"));
    }
}
