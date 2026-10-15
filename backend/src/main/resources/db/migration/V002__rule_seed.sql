-- V002__rule_seed.sql
-- 无用户空库中插入 SYSTEM_SEED 默认规则，再插入 guard 协调行，
-- 避免首位管理员在迁移之后创建的循环依赖（手册 7.2.4 / 7.3）。
INSERT INTO rule_version (version_no, config_json, source, created_by, created_at)
VALUES (1, '{
  "schemaVersion": 1,
  "advanceDays": 2,
  "slotMinutes": 30,
  "minMinutes": 30,
  "maxMinutes": 240,
  "cancelBeforeMinutes": 15,
  "checkinEarlyMinutes": 15,
  "checkinLateMinutes": 15,
  "leaveMaxMinutes": 30,
  "leaveMaxCount": 2,
  "noShowPenalty": 10,
  "leaveTimeoutPenalty": 10,
  "minCredit": 60,
  "creditCap": 100,
  "rewardStartMinutes": 120,
  "rewardBase": 2,
  "rewardStepMinutes": 30,
  "rewardStepScore": 1,
  "rewardCap": 5,
  "endPolicy": "AUTO_COMPLETE_NO_REWARD"
}', 'SYSTEM_SEED', NULL, UTC_TIMESTAMP(3));

INSERT INTO business_guard (id, active_rule_id, updated_at)
VALUES (1, 1, UTC_TIMESTAMP(3));
