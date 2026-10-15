package edu.campus.studyroom.settlement.domain;

import edu.campus.studyroom.reservation.domain.TerminationReason;
import java.time.LocalDateTime;

/** Entity SettlementResult (table: settlement_result). Plain Java class per implementation manual. */
public class SettlementResult {

    private Long reservationId;
    private String terminalEventKey;
    private TerminationReason reason;
    private LocalDateTime finishedAt;
    private Long eligibleSeconds;
    private Integer rewardRequested;
    private Integer rewardActual;
    private Long creditLogId;
    private LocalDateTime createdAt;

    public Long getReservationId() {
        return reservationId;
    }

    public void setReservationId(Long reservationId) {
        this.reservationId = reservationId;
    }

    public String getTerminalEventKey() {
        return terminalEventKey;
    }

    public void setTerminalEventKey(String terminalEventKey) {
        this.terminalEventKey = terminalEventKey;
    }

    public TerminationReason getReason() {
        return reason;
    }

    public void setReason(TerminationReason reason) {
        this.reason = reason;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public Long getEligibleSeconds() {
        return eligibleSeconds;
    }

    public void setEligibleSeconds(Long eligibleSeconds) {
        this.eligibleSeconds = eligibleSeconds;
    }

    public Integer getRewardRequested() {
        return rewardRequested;
    }

    public void setRewardRequested(Integer rewardRequested) {
        this.rewardRequested = rewardRequested;
    }

    public Integer getRewardActual() {
        return rewardActual;
    }

    public void setRewardActual(Integer rewardActual) {
        this.rewardActual = rewardActual;
    }

    public Long getCreditLogId() {
        return creditLogId;
    }

    public void setCreditLogId(Long creditLogId) {
        this.creditLogId = creditLogId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

}
