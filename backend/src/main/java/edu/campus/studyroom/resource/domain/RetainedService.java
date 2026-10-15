package edu.campus.studyroom.resource.domain;

import java.time.LocalDateTime;

/** Entity RetainedService (table: retained_service). Plain Java class per implementation manual. */
public class RetainedService {

    private Long id;
    private Long reservationId;
    private Long seatId;
    private LocalDateTime fromAt;
    private LocalDateTime toAt;
    private Long causeEventId;
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

    public Long getSeatId() {
        return seatId;
    }

    public void setSeatId(Long seatId) {
        this.seatId = seatId;
    }

    public LocalDateTime getFromAt() {
        return fromAt;
    }

    public void setFromAt(LocalDateTime fromAt) {
        this.fromAt = fromAt;
    }

    public LocalDateTime getToAt() {
        return toAt;
    }

    public void setToAt(LocalDateTime toAt) {
        this.toAt = toAt;
    }

    public Long getCauseEventId() {
        return causeEventId;
    }

    public void setCauseEventId(Long causeEventId) {
        this.causeEventId = causeEventId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

}
