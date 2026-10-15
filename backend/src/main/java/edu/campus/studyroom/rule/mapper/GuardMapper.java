package edu.campus.studyroom.rule.mapper;

import edu.campus.studyroom.rule.domain.BusinessGuard;
import org.apache.ibatis.annotations.Mapper;

/**
 * Guard row access. The single row business_guard(id=1) is the write coordination
 * point for all core business commands in this phase.
 */
@Mapper
public interface GuardMapper {

    /** Row lock on the guard row; must be called inside an active transaction. */
    BusinessGuard lockGuard();

    /** Plain read of the guard (e.g. to resolve the active rule). */
    BusinessGuard selectGuard();

    /** Swaps the active rule pointer; called under the guard lock. */
    int updateActiveRule(BusinessGuard guard);
}
