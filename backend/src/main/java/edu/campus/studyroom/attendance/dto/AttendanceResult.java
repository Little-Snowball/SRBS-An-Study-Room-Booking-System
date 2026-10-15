package edu.campus.studyroom.attendance.dto;

import java.time.OffsetDateTime;

/**
 * Attendance command result. Only the fields relevant to the executed command are
 * populated; terminal fields stay null for in-flight commands. eligibleSeconds and
 * the reward pair come from the settlement (or are replayed from settlement_result
 * on an idempotent checkout retry — never re-derived from a later balance).
 */
public record AttendanceResult(
        String reservationId,
        String status,
        OffsetDateTime checkInAt,
        OffsetDateTime leaveDueAt,
        OffsetDateTime finishedAt,
        int leaveCount,
        long eligibleSeconds,
        int rewardRequested,
        int rewardActual
) {
}
