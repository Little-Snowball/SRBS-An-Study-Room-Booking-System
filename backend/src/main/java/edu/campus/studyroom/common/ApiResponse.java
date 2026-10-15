package edu.campus.studyroom.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.slf4j.MDC;

import java.time.OffsetDateTime;

/**
 * Unified success envelope: {"code":"OK","message":"成功","data":{},"traceId":"...","serverTime":"..."}
 * Failures use {@link ErrorEnvelope} so that data stays null and errorDetails
 * only carries field-level validation feedback.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(String code, String message, T data, String traceId, String serverTime) {

    public static final String OK = "OK";

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(OK, "成功", data, currentTraceId(), currentServerTime());
    }

    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(OK, "创建成功", data, currentTraceId(), currentServerTime());
    }

    private static String currentTraceId() {
        String traceId = MDC.get("traceId");
        return traceId == null ? "" : traceId;
    }

    private static String currentServerTime() {
        return OffsetDateTime.now().toString();
    }
}
