package edu.campus.studyroom.user.dto;

/**
 * Manual credit adjustment result: the balances around the applied event and the
 * credit log id. A same-key replay reports the originally logged balances.
 */
public record CreditAdjustResponse(
        int balanceBefore,
        int balanceAfter,
        String logId
) {
}
