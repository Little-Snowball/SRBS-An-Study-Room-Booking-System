package edu.campus.studyroom.credit.domain;

import edu.campus.studyroom.credit.domain.CreditLogType;
import java.time.LocalDateTime;

/** Entity CreditLog (table: credit_log). Plain Java class per implementation manual. */
public class CreditLog {

    private Long id;
    private Long userId;
    private Long reservationId;
    private Long violationId;
    private String eventKey;
    private CreditLogType type;
    private Integer requestedDelta;
    private Integer actualDelta;
    private Integer cappedDelta;
    private Integer balanceBefore;
    private Integer balanceAfter;
    private Long sourceLogId;
    private String reason;
    private Long operatorId;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getReservationId() {
        return reservationId;
    }

    public void setReservationId(Long reservationId) {
        this.reservationId = reservationId;
    }

    public Long getViolationId() {
        return violationId;
    }

    public void setViolationId(Long violationId) {
        this.violationId = violationId;
    }

    public String getEventKey() {
        return eventKey;
    }

    public void setEventKey(String eventKey) {
        this.eventKey = eventKey;
    }

    public CreditLogType getType() {
        return type;
    }

    public void setType(CreditLogType type) {
        this.type = type;
    }

    public Integer getRequestedDelta() {
        return requestedDelta;
    }

    public void setRequestedDelta(Integer requestedDelta) {
        this.requestedDelta = requestedDelta;
    }

    public Integer getActualDelta() {
        return actualDelta;
    }

    public void setActualDelta(Integer actualDelta) {
        this.actualDelta = actualDelta;
    }

    public Integer getCappedDelta() {
        return cappedDelta;
    }

    public void setCappedDelta(Integer cappedDelta) {
        this.cappedDelta = cappedDelta;
    }

    public Integer getBalanceBefore() {
        return balanceBefore;
    }

    public void setBalanceBefore(Integer balanceBefore) {
        this.balanceBefore = balanceBefore;
    }

    public Integer getBalanceAfter() {
        return balanceAfter;
    }

    public void setBalanceAfter(Integer balanceAfter) {
        this.balanceAfter = balanceAfter;
    }

    public Long getSourceLogId() {
        return sourceLogId;
    }

    public void setSourceLogId(Long sourceLogId) {
        this.sourceLogId = sourceLogId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

}
