package edu.campus.studyroom.appeal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Appeal review. APPROVE revokes the violation and refunds the deduction;
 * REJECT requires a comment. expectedVersion is the optimistic lock.
 */
public record ReviewAppealRequest(
        @NotBlank(message = "decision 不能为空")
        @Pattern(regexp = "APPROVE|REJECT", message = "decision 必须为 APPROVE 或 REJECT")
        String decision,

        @Size(max = 1000, message = "审核意见不能超过 1000 字")
        String comment,

        @NotNull(message = "expectedVersion 不能为空")
        Long expectedVersion
) {
}
