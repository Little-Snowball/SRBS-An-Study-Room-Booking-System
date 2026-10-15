package edu.campus.studyroom.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Admin manual credit adjustment. requestedDelta = targetScore - current balance;
 * the change goes through the single CreditService entry so clamping and the
 * credit log are identical to automatic events. Reason is mandatory and audited.
 */
public record CreditAdjustRequest(
        @Min(value = 0, message = "目标信用分不能小于 0") @Max(value = 100, message = "目标信用分不能大于 100") int targetScore,
        @NotBlank @Size(min = 5, max = 500, message = "调整原因须为 5—500 字") String reason
) {
}
