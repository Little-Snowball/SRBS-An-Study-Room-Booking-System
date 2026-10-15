package edu.campus.studyroom.notice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Publish/withdraw command with the optimistic version lock. The optional
 * reason is only used for the withdraw audit trail.
 */
public record NoticeActionRequest(
        @NotNull(message = "expectedVersion 不能为空")
        Long expectedVersion,

        @Size(max = 1000, message = "撤回原因不能超过 1000 字")
        String reason
) {
}
