package edu.campus.studyroom.credit.service;

import edu.campus.studyroom.common.BusinessClock;
import edu.campus.studyroom.common.BusinessException;
import edu.campus.studyroom.credit.CreditCommand;
import edu.campus.studyroom.credit.CreditResult;
import edu.campus.studyroom.credit.CreditService;
import edu.campus.studyroom.credit.domain.CreditAccount;
import edu.campus.studyroom.credit.domain.CreditLog;
import edu.campus.studyroom.credit.mapper.CreditAccountMapper;
import edu.campus.studyroom.credit.mapper.CreditLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 信用事件唯一入口（手册 12.5）。违约扣减、主动签退奖励、申诉退款、人工调整都走这里；
 * 同一 event_key 只入账一次，余额钳制在 schema 约束的 0—100（credit_account CK 与
 * credit_log CK 均为 0—100，属于结构不变量而非可配置规则）。
 * <p>事务约定：apply 为 MANDATORY——必须在调用方已开启且持有 guard 锁的事务内调用
 * （结算、申诉审核、人工调整均如此）。被单独调用而没有活动事务时会抛出
 * IllegalTransactionStateException，调用方需先自行开启事务。
 */
@Service
public class CreditServiceImpl implements CreditService {

    /** schema CK：credit_account.score / credit_log.balance_* 均约束在 0—100。 */
    private static final int CREDIT_MIN = 0;
    private static final int CREDIT_MAX = 100;

    private final CreditAccountMapper creditAccountMapper;
    private final CreditLogMapper creditLogMapper;
    private final BusinessClock clock;

    public CreditServiceImpl(CreditAccountMapper creditAccountMapper, CreditLogMapper creditLogMapper,
                             BusinessClock clock) {
        this.creditAccountMapper = creditAccountMapper;
        this.creditLogMapper = creditLogMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public CreditResult apply(CreditCommand command) {
        if (command == null || command.eventKey() == null || command.eventKey().isBlank()
                || command.userId() == null || command.type() == null) {
            throw BusinessException.validation("信用事件参数不完整");
        }
        // 1. 按 event_key 查重：已入账则原值重放，不重复变更余额
        CreditLog existing = creditLogMapper.selectByEventKey(command.eventKey());
        if (existing != null) {
            return new CreditResult(existing.getId(), existing.getActualDelta(), existing.getBalanceBefore(),
                    existing.getBalanceAfter(), true);
        }
        // 2. 锁定信用行后计算：before=score，after=clamp(before+requested,0,100)
        CreditAccount account = creditAccountMapper.selectByUserIdForUpdate(command.userId());
        if (account == null) {
            throw BusinessException.internal("信用账户缺失，用户 id=" + command.userId());
        }
        int before = account.getScore();
        int requested = command.requestedDelta();
        long raw = (long) before + requested;
        int after = (int) Math.max(CREDIT_MIN, Math.min(CREDIT_MAX, raw));
        int actual = after - before;
        int capped = requested - actual;
        // 3. 流水（CK 保证 balance_after = balance_before + actual_delta、
        //    capped_delta = requested_delta - actual_delta；零变动也写流水）
        CreditLog log = new CreditLog();
        log.setUserId(command.userId());
        log.setReservationId(command.reservationId());
        log.setViolationId(command.violationId());
        log.setEventKey(command.eventKey());
        log.setType(command.type());
        log.setRequestedDelta(requested);
        log.setActualDelta(actual);
        log.setCappedDelta(capped);
        log.setBalanceBefore(before);
        log.setBalanceAfter(after);
        log.setSourceLogId(command.sourceLogId());
        log.setReason(command.reason());
        log.setOperatorId(command.operatorId());
        log.setCreatedAt(clock.nowLocal());
        creditLogMapper.insert(log);
        // 4. 条件更新余额（expectedVersion 防丢失更新），affectedRows 必须为 1
        int rows = creditAccountMapper.updateScore(command.userId(), account.getVersion(), after,
                clock.nowLocal());
        if (rows != 1) {
            throw BusinessException.invalidState("信用账户版本冲突，请重试");
        }
        return new CreditResult(log.getId(), actual, before, after, false);
    }

    @Override
    public int currentScore(Long userId) {
        CreditAccount account = creditAccountMapper.selectByUserId(userId);
        // 防御：账户行缺失时按满分返回，查询视图不因缺行报错
        return account == null ? CREDIT_MAX : account.getScore();
    }
}
