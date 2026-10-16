package edu.campus.studyroom.user.dto;

import edu.campus.studyroom.user.domain.UserStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Admin status change for a student account. expectedAuthVersion guards against
 * lost updates (affectedRows=0 becomes 409 INVALID_STATE). When disabling,
 * confirmTerminate acknowledges the no-fault termination of the account's
 * effective reservations; without it the request is rejected with 422.
 */
public record UserStatusRequest(
        @NotNull(message = "status 不能为空") UserStatus status,
        @Size(max = 500, message = "操作原因不能超过 500 字") String reason,
        int expectedAuthVersion,
        boolean confirmTerminate
) {
}
