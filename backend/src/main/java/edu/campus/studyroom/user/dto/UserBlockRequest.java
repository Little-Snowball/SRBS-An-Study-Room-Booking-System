package edu.campus.studyroom.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Admin manual black-list toggle. blocked=true no-fault terminates the account's
 * effective reservations and revokes its sessions in the same transaction;
 * blocked=false only clears the manual flag and never bypasses the credit gate.
 */
public record UserBlockRequest(
        boolean blocked,
        @NotBlank(message = "操作原因不能为空") String reason
) {
}
