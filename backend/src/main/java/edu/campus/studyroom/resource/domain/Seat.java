package edu.campus.studyroom.resource.domain;

import edu.campus.studyroom.resource.domain.SeatStatus;
import java.time.LocalDateTime;

/** Entity Seat (table: seat). Plain Java class per implementation manual. */
public class Seat {

    private Long id;
    private Long roomId;
    private String code;
    private Integer rowNo;
    private Integer colNo;
    private Boolean hasPower;
    private Boolean nearWindow;
    private Boolean quietZone;
    private SeatStatus status;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRoomId() {
        return roomId;
    }

    public void setRoomId(Long roomId) {
        this.roomId = roomId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Integer getRowNo() {
        return rowNo;
    }

    public void setRowNo(Integer rowNo) {
        this.rowNo = rowNo;
    }

    public Integer getColNo() {
        return colNo;
    }

    public void setColNo(Integer colNo) {
        this.colNo = colNo;
    }

    public Boolean isHasPower() {
        return hasPower;
    }

    public void setHasPower(Boolean hasPower) {
        this.hasPower = hasPower;
    }

    public Boolean isNearWindow() {
        return nearWindow;
    }

    public void setNearWindow(Boolean nearWindow) {
        this.nearWindow = nearWindow;
    }

    public Boolean isQuietZone() {
        return quietZone;
    }

    public void setQuietZone(Boolean quietZone) {
        this.quietZone = quietZone;
    }

    public SeatStatus getStatus() {
        return status;
    }

    public void setStatus(SeatStatus status) {
        this.status = status;
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
