package edu.campus.studyroom.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.slf4j.MDC;

import java.time.OffsetDateTime;

/**
 * Unified failure envelope: {"code":"SLOT_CONFLICT","message":"...","data":null,
 * "traceId":"...","serverTime":"...","errorDetails":{...}}
 * errorDetails is only used for field-level validation feedback and never
 * leaks SQL/stack/password material.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorEnvelope(String code, String message, Object data, String traceId,
                            String serverTime, Object errorDetails) {

    public static ErrorEnvelope of(ErrorCode code, String message) {
        return of(code, message, null);
    }

    public static ErrorEnvelope of(ErrorCode code, String message, Object errorDetails) {
        return new ErrorEnvelope(code.name(), message, null, currentTraceId(), currentServerTime(), errorDetails);
    }

    private static String currentTraceId() {
        String traceId = MDC.get("traceId");
        return traceId == null ? "" : traceId;
    }

    private static String currentServerTime() {
        return OffsetDateTime.now().toString();
    }
}
