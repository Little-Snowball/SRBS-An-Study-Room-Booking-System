package edu.campus.studyroom.rule;

import edu.campus.studyroom.common.BusinessException;
import edu.campus.studyroom.common.ErrorCode;
import edu.campus.studyroom.rule.domain.BusinessGuard;
import edu.campus.studyroom.rule.mapper.GuardMapper;
import org.springframework.stereotype.Component;

/**
 * Serializes core business writes through the single business_guard row.
 * Only transaction-coordinating services call this; it must run inside a
 * READ COMMITTED transaction that has already started.
 */
@Component
public class BusinessGuardLock {

    private final GuardMapper guardMapper;

    public BusinessGuardLock(GuardMapper guardMapper) {
        this.guardMapper = guardMapper;
    }

    /** Acquires the guard row lock. Throws when the row is missing (misconfiguration). */
    public void lock() {
        BusinessGuard guard = guardMapper.lockGuard();
        if (guard == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "business_guard 协调行缺失，请检查迁移 V002 是否执行");
        }
    }
}
