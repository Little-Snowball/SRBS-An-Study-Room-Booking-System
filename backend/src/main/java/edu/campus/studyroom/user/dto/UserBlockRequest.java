package edu.campus.studyroom.user.dto;

import jakarta.validation.constraints.Size;

/**
 * Admin manual black-list toggle. blocked=true no-fault terminates the account's
 * effective reservations and revokes its sessions in the same transaction;
 * blocked=false only clears the manual flag and never bypasses the credit gate.
 * The reason is optional and only recorded for the audit trail.
 */
public record UserBlockRequest(
        boolean blocked,
        @Size(max = 500, message = "操作原因不能超过 500 字") String reason
) {
}
