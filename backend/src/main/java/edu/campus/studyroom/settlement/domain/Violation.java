package edu.campus.studyroom.settlement.domain;

import edu.campus.studyroom.settlement.domain.ViolationReason;
import edu.campus.studyroom.settlement.domain.ViolationStatus;
import java.time.LocalDateTime;

/** Entity Violation (table: violation). Plain Java class per implementation manual. */
public class Violation {

    private Long id;
    private Long reservationId;
    private Long userId;
    private ViolationReason reason;
    private ViolationStatus status;
    private LocalDateTime eventAt;
    private LocalDateTime processedAt;
    private LocalDateTime notifiedAt;
    private Long deductionLogId;
    private LocalDateTime revokedAt;
    private Long revokedBy;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getReservationId() {
        return reservationId;
    }

    public void setReservationId(Long reservationId) {
        this.reservationId = reservationId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public ViolationReason getReason() {
        return reason;
    }

    public void setReason(ViolationReason reason) {
        this.reason = reason;
    }

    public ViolationStatus getStatus() {
        return status;
    }

    public void setStatus(ViolationStatus status) {
        this.status = status;
    }

    public LocalDateTime getEventAt() {
        return eventAt;
    }

    public void setEventAt(LocalDateTime eventAt) {
        this.eventAt = eventAt;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }

    public LocalDateTime getNotifiedAt() {
        return notifiedAt;
    }

    public void setNotifiedAt(LocalDateTime notifiedAt) {
        this.notifiedAt = notifiedAt;
    }

    public Long getDeductionLogId() {
        return deductionLogId;
    }

    public void setDeductionLogId(Long deductionLogId) {
        this.deductionLogId = deductionLogId;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(LocalDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }

    public Long getRevokedBy() {
        return revokedBy;
    }

    public void setRevokedBy(Long revokedBy) {
        this.revokedBy = revokedBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

}
