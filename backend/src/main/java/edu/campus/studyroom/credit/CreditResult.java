package edu.campus.studyroom.credit;

/**
 * Result of a credit application. replayed=true means the eventKey already
 * existed and the original log values are returned.
 */
public record CreditResult(Long logId, int actualDelta, int balanceBefore, int balanceAfter,
                           boolean replayed) {
}
