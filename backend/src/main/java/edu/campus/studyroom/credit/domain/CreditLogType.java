package edu.campus.studyroom.credit.domain;

/** Credit log type; source_log_id only points at an original DEBIT log for refunds. */
public enum CreditLogType {
    NO_SHOW, LEAVE_TIMEOUT, REWARD, APPEAL_REFUND, MANUAL_ADJUST
}
