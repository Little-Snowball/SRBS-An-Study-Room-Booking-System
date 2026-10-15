package edu.campus.studyroom.resource.domain;

import edu.campus.studyroom.resource.domain.OrderPolicy;
import edu.campus.studyroom.resource.domain.ResourceEventType;
import edu.campus.studyroom.resource.domain.ResourceType;
import java.time.LocalDateTime;

/** Entity ResourceStatusEvent (table: resource_status_event). Plain Java class per implementation manual. */
public class ResourceStatusEvent {

    private Long id;
    private ResourceType resourceType;
    private ResourceEventType eventType;
    private Long roomId;
    private Long seatId;
    private String afterSnapshot;
    private OrderPolicy orderPolicy;
    private LocalDateTime effectiveAt;
    private Long operatorId;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ResourceType getResourceType() {
        return resourceType;
    }

    public void setResourceType(ResourceType resourceType) {
        this.resourceType = resourceType;
    }

    public ResourceEventType getEventType() {
        return eventType;
    }

    public void setEventType(ResourceEventType eventType) {
        this.eventType = eventType;
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

    public String getAfterSnapshot() {
        return afterSnapshot;
    }

    public void setAfterSnapshot(String afterSnapshot) {
        this.afterSnapshot = afterSnapshot;
    }

    public OrderPolicy getOrderPolicy() {
        return orderPolicy;
    }

    public void setOrderPolicy(OrderPolicy orderPolicy) {
        this.orderPolicy = orderPolicy;
    }

    public LocalDateTime getEffectiveAt() {
        return effectiveAt;
    }

    public void setEffectiveAt(LocalDateTime effectiveAt) {
        this.effectiveAt = effectiveAt;
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
