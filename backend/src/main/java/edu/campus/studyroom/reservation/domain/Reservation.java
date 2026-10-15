package edu.campus.studyroom.reservation.domain;

import edu.campus.studyroom.reservation.domain.ReservationStatus;
import edu.campus.studyroom.reservation.domain.TerminationReason;
import java.time.LocalDateTime;

/** Entity Reservation (table: reservation). Plain Java class per implementation manual. */
public class Reservation {

    private Long id;
    private String orderNo;
    private Long userId;
    private Long roomId;
    private Long seatId;
    private String roomNameSnapshot;
    private String seatCodeSnapshot;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private ReservationStatus status;
    private Long ruleVersionId;
    private String ruleSnapshot;
    private LocalDateTime checkinDueAt;
    private LocalDateTime cancelDueAt;
    private LocalDateTime checkInAt;
    private LocalDateTime lastLeaveAt;
    private LocalDateTime leaveDueAt;
    private LocalDateTime finishedAt;
    private Integer leaveCount;
    private TerminationReason terminationReason;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getRoomId() {
        return roomId;
    }

    public void setRoomId(Long roomId) {
        this.roomId = roomId;
    }

    public Long getSeatId() {
        return seatId;
    }

    public void setSeatId(Long seatId) {
        this.seatId = seatId;
    }

    public String getRoomNameSnapshot() {
        return roomNameSnapshot;
    }

    public void setRoomNameSnapshot(String roomNameSnapshot) {
        this.roomNameSnapshot = roomNameSnapshot;
    }

    public String getSeatCodeSnapshot() {
        return seatCodeSnapshot;
    }

    public void setSeatCodeSnapshot(String seatCodeSnapshot) {
        this.seatCodeSnapshot = seatCodeSnapshot;
    }

    public LocalDateTime getStartAt() {
        return startAt;
    }

    public void setStartAt(LocalDateTime startAt) {
        this.startAt = startAt;
    }

    public LocalDateTime getEndAt() {
        return endAt;
    }

    public void setEndAt(LocalDateTime endAt) {
        this.endAt = endAt;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public void setStatus(ReservationStatus status) {
        this.status = status;
    }

    public Long getRuleVersionId() {
        return ruleVersionId;
    }

    public void setRuleVersionId(Long ruleVersionId) {
        this.ruleVersionId = ruleVersionId;
    }

    public String getRuleSnapshot() {
        return ruleSnapshot;
    }

    public void setRuleSnapshot(String ruleSnapshot) {
        this.ruleSnapshot = ruleSnapshot;
    }

    public LocalDateTime getCheckinDueAt() {
        return checkinDueAt;
    }

    public void setCheckinDueAt(LocalDateTime checkinDueAt) {
        this.checkinDueAt = checkinDueAt;
    }

    public LocalDateTime getCancelDueAt() {
        return cancelDueAt;
    }

    public void setCancelDueAt(LocalDateTime cancelDueAt) {
        this.cancelDueAt = cancelDueAt;
    }

    public LocalDateTime getCheckInAt() {
        return checkInAt;
    }

    public void setCheckInAt(LocalDateTime checkInAt) {
        this.checkInAt = checkInAt;
    }

    public LocalDateTime getLastLeaveAt() {
        return lastLeaveAt;
    }

    public void setLastLeaveAt(LocalDateTime lastLeaveAt) {
        this.lastLeaveAt = lastLeaveAt;
    }

    public LocalDateTime getLeaveDueAt() {
        return leaveDueAt;
    }

    public void setLeaveDueAt(LocalDateTime leaveDueAt) {
        this.leaveDueAt = leaveDueAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public Integer getLeaveCount() {
        return leaveCount;
    }

    public void setLeaveCount(Integer leaveCount) {
        this.leaveCount = leaveCount;
    }

    public TerminationReason getTerminationReason() {
        return terminationReason;
    }

    public void setTerminationReason(TerminationReason terminationReason) {
        this.terminationReason = terminationReason;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

}
