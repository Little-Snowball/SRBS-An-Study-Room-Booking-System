package edu.campus.studyroom.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Admin manual credit adjustment. requestedDelta = targetScore - current balance;
 * the change goes through the single CreditService entry so clamping and the
 * credit log are identical to automatic events. Reason is optional and audited.
 */
public record CreditAdjustRequest(
        @Min(value = 0, message = "目标信用分不能小于 0") @Max(value = 100, message = "目标信用分不能大于 100") int targetScore,
        @Size(max = 500, message = "调整原因不能超过 500 字") String reason
) {
}
