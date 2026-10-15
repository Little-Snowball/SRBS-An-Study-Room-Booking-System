package edu.campus.studyroom.user.dto;

import edu.campus.studyroom.user.domain.UserStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Admin status change for a student account. expectedAuthVersion guards against
 * lost updates (affectedRows=0 becomes 409 INVALID_STATE). When disabling,
 * confirmTerminate acknowledges the no-fault termination of the account's
 * effective reservations; without it the request is rejected with 422.
 */
public record UserStatusRequest(
        @NotNull(message = "status 不能为空") UserStatus status,
        @NotBlank(message = "操作原因不能为空") String reason,
        int expectedAuthVersion,
        boolean confirmTerminate
) {
}
