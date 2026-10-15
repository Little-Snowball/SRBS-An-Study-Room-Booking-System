package edu.campus.studyroom.rule.domain;

import edu.campus.studyroom.rule.domain.RuleSource;
import java.time.LocalDateTime;

/** Entity RuleVersion (table: rule_version). Plain Java class per implementation manual. */
public class RuleVersion {

    private Long id;
    private Integer versionNo;
    private String configJson;
    private RuleSource source;
    private Long createdBy;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson;
    }

    public RuleSource getSource() {
        return source;
    }

    public void setSource(RuleSource source) {
        this.source = source;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

}
