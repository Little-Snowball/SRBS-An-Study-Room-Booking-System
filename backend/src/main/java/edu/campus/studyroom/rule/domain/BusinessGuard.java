package edu.campus.studyroom.rule.domain;

import java.time.LocalDateTime;

/** Entity BusinessGuard (table: business_guard). Plain Java class per implementation manual. */
public class BusinessGuard {

    private Integer id;
    private Long activeRuleId;
    private LocalDateTime updatedAt;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Long getActiveRuleId() {
        return activeRuleId;
    }

    public void setActiveRuleId(Long activeRuleId) {
        this.activeRuleId = activeRuleId;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

}
