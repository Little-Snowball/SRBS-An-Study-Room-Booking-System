-- V001__schema.sql
-- 高校自习室预约与考勤管理系统 — 完整表结构
-- 依据《完整开发实施手册》第 7 章字段字典。InnoDB / utf8mb4 / DATETIME(3) /
-- 显式外键 ON DELETE RESTRICT / 全部外键列建索引。禁止级联删除。
-- 执行顺序遵循 7.3：先用户与信用，再资源，再规则与 guard，
-- 再订单与考勤、credit_log（无 violation FK）、violation、回填 violation FK，
-- 再申诉/文件/会话/通知/公告/审计/幂等、结算、资源事件、保留服务。

CREATE TABLE app_user (
  id                   BIGINT       NOT NULL AUTO_INCREMENT,
  account              VARCHAR(32)  NOT NULL COMMENT '学号/工号，统一大写去空格',
  password_hash        VARCHAR(100) NOT NULL COMMENT 'BCrypt',
  name                 VARCHAR(64)  NOT NULL,
  college              VARCHAR(100) NULL,
  phone                VARCHAR(20)  NULL,
  role                 VARCHAR(16)  NOT NULL COMMENT 'STUDENT/ADMIN',
  status               VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
  blocked              BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '人工黑名单，独立于信用不足',
  must_change_password BOOLEAN      NOT NULL DEFAULT FALSE,
  auth_version         INT          NOT NULL DEFAULT 0 COMMENT '改密/重置/禁用时递增，注销全部会话',
  created_at           DATETIME(3)  NOT NULL,
  updated_at           DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_app_user_account (account),
  CONSTRAINT ck_app_user_role CHECK (role IN ('STUDENT', 'ADMIN')),
  CONSTRAINT ck_app_user_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户账号';

CREATE TABLE credit_account (
  user_id    BIGINT      NOT NULL,
  score      INT         NOT NULL DEFAULT 100,
  version    BIGINT      NOT NULL DEFAULT 0,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (user_id),
  CONSTRAINT ck_credit_account_score CHECK (score BETWEEN 0 AND 100),
  CONSTRAINT fk_credit_account_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '信用账户，0-100';

CREATE TABLE study_room (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  code             VARCHAR(32)  NOT NULL COMMENT '房间编码，不可随普通编辑变更',
  name             VARCHAR(100) NOT NULL,
  campus           VARCHAR(64)  NOT NULL,
  building         VARCHAR(64)  NOT NULL,
  floor_no         INT          NOT NULL,
  open_time        TIME         NOT NULL,
  close_time       TIME         NOT NULL,
  rows_count       INT          NOT NULL,
  cols_count       INT          NOT NULL,
  checkin_code_hash VARCHAR(100) NOT NULL COMMENT '固定六位现场码 BCrypt 哈希',
  status           VARCHAR(16)  NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/CLOSED',
  version          BIGINT       NOT NULL DEFAULT 0,
  created_at       DATETIME(3)  NOT NULL,
  updated_at       DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_study_room_code (code),
  KEY idx_study_room_status (status),
  CONSTRAINT ck_study_room_hours CHECK (open_time < close_time),
  CONSTRAINT ck_study_room_rows CHECK (rows_count BETWEEN 1 AND 100),
  CONSTRAINT ck_study_room_cols CHECK (cols_count BETWEEN 1 AND 100),
  CONSTRAINT ck_study_room_status CHECK (status IN ('OPEN', 'CLOSED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '自习室';

CREATE TABLE seat (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  room_id    BIGINT       NOT NULL,
  code       VARCHAR(32)  NOT NULL COMMENT '如 A-R01-C02',
  row_no     INT          NOT NULL,
  col_no     INT          NOT NULL,
  has_power  BOOLEAN      NOT NULL DEFAULT FALSE,
  near_window BOOLEAN     NOT NULL DEFAULT FALSE,
  quiet_zone BOOLEAN      NOT NULL DEFAULT FALSE,
  status     VARCHAR(16)  NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/MAINTENANCE',
  version    BIGINT       NOT NULL DEFAULT 0,
  created_at DATETIME(3)  NOT NULL,
  updated_at DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_seat_room_code (room_id, code),
  UNIQUE KEY uk_seat_room_rc (room_id, row_no, col_no),
  KEY idx_seat_room_status (room_id, status),
  CONSTRAINT fk_seat_room FOREIGN KEY (room_id) REFERENCES study_room (id) ON DELETE RESTRICT,
  CONSTRAINT ck_seat_status CHECK (status IN ('NORMAL', 'MAINTENANCE'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '座位';

CREATE TABLE rule_version (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  version_no  INT         NOT NULL COMMENT '全局递增版本号',
  config_json JSON        NOT NULL COMMENT '完整规则快照 JSON',
  source      VARCHAR(20) NOT NULL COMMENT 'SYSTEM_SEED/ADMIN_PUBLISH',
  created_by  BIGINT      NULL COMMENT 'SYSTEM_SEED 可空，ADMIN_PUBLISH 必须非空',
  created_at  DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_rule_version_no (version_no),
  KEY idx_rule_version_created_by (created_by),
  CONSTRAINT fk_rule_version_user FOREIGN KEY (created_by) REFERENCES app_user (id) ON DELETE RESTRICT,
  CONSTRAINT ck_rule_version_source CHECK (source IN ('SYSTEM_SEED', 'ADMIN_PUBLISH'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '规则版本，发布后只读';

CREATE TABLE business_guard (
  id             TINYINT     NOT NULL,
  active_rule_id BIGINT      NOT NULL COMMENT '当前生效规则指针，兼作写事务串行化协调行',
  updated_at     DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT ck_guard_singleton CHECK (id = 1),
  CONSTRAINT fk_guard_rule FOREIGN KEY (active_rule_id) REFERENCES rule_version (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '全局写协调行，不得删除';

CREATE TABLE reservation (
  id                   BIGINT       NOT NULL AUTO_INCREMENT,
  order_no             VARCHAR(40)  NOT NULL,
  user_id              BIGINT       NOT NULL,
  room_id              BIGINT       NOT NULL,
  seat_id              BIGINT       NOT NULL,
  room_name_snapshot   VARCHAR(100) NOT NULL,
  seat_code_snapshot   VARCHAR(32)  NOT NULL,
  start_at             DATETIME(3)  NOT NULL,
  end_at               DATETIME(3)  NOT NULL,
  status               VARCHAR(16)  NOT NULL COMMENT 'RESERVED/IN_USE/LEFT_TEMP/COMPLETED/CANCELLED/VIOLATED',
  rule_version_id      BIGINT       NOT NULL,
  rule_snapshot        JSON         NOT NULL COMMENT '下单时规则快照',
  checkin_due_at       DATETIME(3)  NOT NULL COMMENT 'min(start+lateGrace, end)',
  cancel_due_at        DATETIME(3)  NOT NULL COMMENT 'start-cancelBeforeMinutes',
  check_in_at          DATETIME(3)  NULL,
  last_leave_at        DATETIME(3)  NULL,
  leave_due_at         DATETIME(3)  NULL,
  finished_at          DATETIME(3)  NULL,
  leave_count          INT          NOT NULL DEFAULT 0,
  termination_reason   VARCHAR(32)  NULL,
  version              BIGINT       NOT NULL DEFAULT 0,
  created_at           DATETIME(3)  NOT NULL,
  updated_at           DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_reservation_order_no (order_no),
  KEY idx_reservation_seat (seat_id, status, start_at, end_at),
  KEY idx_reservation_user (user_id, status, start_at, end_at),
  KEY idx_reservation_checkin_due (status, checkin_due_at),
  KEY idx_reservation_leave_due (status, leave_due_at),
  KEY idx_reservation_end (status, end_at),
  KEY idx_reservation_room (room_id),
  KEY idx_reservation_rule_version (rule_version_id),
  CONSTRAINT fk_reservation_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE RESTRICT,
  CONSTRAINT fk_reservation_room FOREIGN KEY (room_id) REFERENCES study_room (id) ON DELETE RESTRICT,
  CONSTRAINT fk_reservation_seat FOREIGN KEY (seat_id) REFERENCES seat (id) ON DELETE RESTRICT,
  CONSTRAINT fk_reservation_rule FOREIGN KEY (rule_version_id) REFERENCES rule_version (id) ON DELETE RESTRICT,
  CONSTRAINT ck_reservation_range CHECK (start_at < end_at),
  CONSTRAINT ck_reservation_leave_count CHECK (leave_count >= 0),
  CONSTRAINT ck_reservation_status CHECK (status IN ('RESERVED', 'IN_USE', 'LEFT_TEMP', 'COMPLETED', 'CANCELLED', 'VIOLATED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '预约订单，历史不物理删除';

CREATE TABLE attendance_event (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  reservation_id BIGINT       NOT NULL,
  event_type     VARCHAR(32)  NOT NULL COMMENT 'CHECK_IN/LEAVE/RETURN/CHECK_OUT/AUTO_END/FORCE_END/VIOLATION_END/CANCEL',
  event_at       DATETIME(3)  NOT NULL COMMENT '业务发生时间（截止点或实际操作时间）',
  operator_id    BIGINT       NULL COMMENT '系统操作为空',
  request_key    VARCHAR(64)  NOT NULL COMMENT '幂等键，与命令幂等共用',
  created_at     DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_attendance_res_key (reservation_id, request_key),
  KEY idx_attendance_res_time (reservation_id, event_at, id),
  KEY idx_attendance_operator (operator_id),
  CONSTRAINT fk_attendance_res FOREIGN KEY (reservation_id) REFERENCES reservation (id) ON DELETE RESTRICT,
  CONSTRAINT fk_attendance_user FOREIGN KEY (operator_id) REFERENCES app_user (id) ON DELETE RESTRICT,
  CONSTRAINT ck_attendance_type CHECK (event_type IN ('CHECK_IN', 'LEAVE', 'RETURN', 'CHECK_OUT', 'AUTO_END', 'FORCE_END', 'VIOLATION_END', 'CANCEL'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '考勤事件，仅成功迁移写入';

CREATE TABLE credit_log (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  user_id         BIGINT       NOT NULL,
  reservation_id  BIGINT       NULL COMMENT '人工调整为空',
  violation_id    BIGINT       NULL COMMENT '后补外键，避免循环依赖',
  event_key       VARCHAR(100) NOT NULL COMMENT '业务事件唯一键，保证同一事件只入账一次',
  type            VARCHAR(32)  NOT NULL COMMENT 'NO_SHOW/LEAVE_TIMEOUT/REWARD/APPEAL_REFUND/MANUAL_ADJUST',
  requested_delta INT          NOT NULL COMMENT '规则请求值',
  actual_delta    INT          NOT NULL COMMENT '实际入账值',
  capped_delta    INT          NOT NULL COMMENT '被 0-100 封顶削去的部分',
  balance_before  INT          NOT NULL,
  balance_after   INT          NOT NULL,
  source_log_id   BIGINT       NULL COMMENT '补偿时指向原扣分流水',
  reason          VARCHAR(1000) NOT NULL,
  operator_id     BIGINT       NULL,
  created_at      DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_credit_log_event_key (event_key),
  KEY idx_credit_log_user (user_id, id),
  KEY idx_credit_log_reservation (reservation_id),
  KEY idx_credit_log_operator (operator_id),
  KEY idx_credit_log_source (source_log_id),
  CONSTRAINT fk_credit_log_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE RESTRICT,
  CONSTRAINT fk_credit_log_res FOREIGN KEY (reservation_id) REFERENCES reservation (id) ON DELETE RESTRICT,
  CONSTRAINT fk_credit_log_operator FOREIGN KEY (operator_id) REFERENCES app_user (id) ON DELETE RESTRICT,
  CONSTRAINT fk_credit_log_source FOREIGN KEY (source_log_id) REFERENCES credit_log (id) ON DELETE RESTRICT,
  CONSTRAINT ck_credit_log_type CHECK (type IN ('NO_SHOW', 'LEAVE_TIMEOUT', 'REWARD', 'APPEAL_REFUND', 'MANUAL_ADJUST')),
  CONSTRAINT ck_credit_log_before CHECK (balance_before BETWEEN 0 AND 100),
  CONSTRAINT ck_credit_log_after CHECK (balance_after BETWEEN 0 AND 100),
  CONSTRAINT ck_credit_log_after_sum CHECK (balance_after = balance_before + actual_delta),
  CONSTRAINT ck_credit_log_capped CHECK (capped_delta = requested_delta - actual_delta)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '信用流水，不物理删除';

CREATE TABLE violation (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  reservation_id    BIGINT       NOT NULL COMMENT '本期每单最多一个终态违约',
  user_id           BIGINT       NOT NULL,
  reason            VARCHAR(32)  NOT NULL COMMENT 'NO_SHOW/LEAVE_TIMEOUT',
  status            VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/REVOKED',
  event_at          DATETIME(3)  NOT NULL COMMENT '违约业务发生时间（截止点）',
  processed_at      DATETIME(3)  NOT NULL COMMENT '实际处理时间',
  notified_at       DATETIME(3)  NOT NULL COMMENT '通知可得知时刻，申诉窗口起算点',
  deduction_log_id  BIGINT       NULL COMMENT '同事务回填的扣分流水',
  revoked_at        DATETIME(3)  NULL,
  revoked_by        BIGINT       NULL,
  created_at        DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_violation_reservation (reservation_id),
  KEY idx_violation_user_event (user_id, event_at),
  KEY idx_violation_revoked_by (revoked_by),
  CONSTRAINT fk_violation_res FOREIGN KEY (reservation_id) REFERENCES reservation (id) ON DELETE RESTRICT,
  CONSTRAINT fk_violation_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE RESTRICT,
  CONSTRAINT fk_violation_revoker FOREIGN KEY (revoked_by) REFERENCES app_user (id) ON DELETE RESTRICT,
  CONSTRAINT fk_violation_deduction FOREIGN KEY (deduction_log_id) REFERENCES credit_log (id) ON DELETE RESTRICT,
  CONSTRAINT ck_violation_reason CHECK (reason IN ('NO_SHOW', 'LEAVE_TIMEOUT')),
  CONSTRAINT ck_violation_status CHECK (status IN ('ACTIVE', 'REVOKED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '违约记录，撤销不物理删除';

ALTER TABLE credit_log
  ADD CONSTRAINT fk_credit_log_violation FOREIGN KEY (violation_id) REFERENCES violation (id) ON DELETE RESTRICT;

CREATE TABLE appeal (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  violation_id   BIGINT       NOT NULL COMMENT '一笔处罚唯一工单',
  user_id        BIGINT       NOT NULL,
  reason         VARCHAR(1000) NOT NULL COMMENT '10-1000 字',
  status         VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
  reviewer_id    BIGINT       NULL,
  review_comment VARCHAR(1000) NULL,
  submitted_at   DATETIME(3)  NOT NULL,
  reviewed_at    DATETIME(3)  NULL,
  version        BIGINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_appeal_violation (violation_id),
  KEY idx_appeal_user (user_id),
  KEY idx_appeal_reviewer (reviewer_id),
  KEY idx_appeal_status_submitted (status, submitted_at),
  CONSTRAINT fk_appeal_violation FOREIGN KEY (violation_id) REFERENCES violation (id) ON DELETE RESTRICT,
  CONSTRAINT fk_appeal_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE RESTRICT,
  CONSTRAINT fk_appeal_reviewer FOREIGN KEY (reviewer_id) REFERENCES app_user (id) ON DELETE RESTRICT,
  CONSTRAINT ck_appeal_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '违约申诉';

CREATE TABLE file_object (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  owner_id      BIGINT       NOT NULL,
  storage_key   VARCHAR(100) NOT NULL COMMENT '应用生成的 UUID，不暴露磁盘路径',
  original_name VARCHAR(255) NOT NULL COMMENT '仅展示用',
  mime_type     VARCHAR(32)  NOT NULL,
  size_bytes    BIGINT       NOT NULL,
  sha256        CHAR(64)     NOT NULL,
  state         VARCHAR(16)  NOT NULL DEFAULT 'TEMP' COMMENT 'TEMP/BOUND/QUARANTINED',
  created_at    DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_file_object_storage_key (storage_key),
  KEY idx_file_object_owner (owner_id),
  KEY idx_file_object_state (state, created_at),
  CONSTRAINT fk_file_object_owner FOREIGN KEY (owner_id) REFERENCES app_user (id) ON DELETE RESTRICT,
  CONSTRAINT ck_file_object_state CHECK (state IN ('TEMP', 'BOUND', 'QUARANTINED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '凭证文件元数据';

CREATE TABLE appeal_file (
  appeal_id BIGINT NOT NULL,
  file_id   BIGINT NOT NULL,
  PRIMARY KEY (appeal_id, file_id),
  UNIQUE KEY uk_appeal_file_file (file_id),
  CONSTRAINT fk_appeal_file_appeal FOREIGN KEY (appeal_id) REFERENCES appeal (id) ON DELETE RESTRICT,
  CONSTRAINT fk_appeal_file_file FOREIGN KEY (file_id) REFERENCES file_object (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '申诉凭证绑定，禁止跨申诉重复绑定';

CREATE TABLE auth_session (
  id           CHAR(36)     NOT NULL COMMENT 'JWT sid',
  user_id      BIGINT       NOT NULL,
  auth_version INT          NOT NULL COMMENT '签发时的账号版本',
  expires_at   DATETIME(3)  NOT NULL,
  revoked_at   DATETIME(3)  NULL,
  created_at   DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  KEY idx_auth_session_user (user_id, expires_at),
  CONSTRAINT fk_auth_session_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '服务端会话，每请求校验';

CREATE TABLE notification (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  user_id        BIGINT       NOT NULL,
  event_key      VARCHAR(100) NOT NULL,
  type           VARCHAR(32)  NOT NULL,
  title          VARCHAR(100) NOT NULL,
  content        VARCHAR(1000) NOT NULL,
  reservation_id BIGINT       NULL,
  violation_id   BIGINT       NULL,
  read_at        DATETIME(3)  NULL,
  published_at   DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_notification_event_key (event_key),
  KEY idx_notification_user (user_id, published_at),
  KEY idx_notification_res (reservation_id),
  KEY idx_notification_violation (violation_id),
  CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE RESTRICT,
  CONSTRAINT fk_notification_res FOREIGN KEY (reservation_id) REFERENCES reservation (id) ON DELETE RESTRICT,
  CONSTRAINT fk_notification_violation FOREIGN KEY (violation_id) REFERENCES violation (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '站内通知，处罚/取消与业务变更同事务';

CREATE TABLE notice (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  title        VARCHAR(100) NOT NULL,
  content      TEXT         NOT NULL COMMENT '纯文本，最多 5000 字',
  pinned       BOOLEAN      NOT NULL DEFAULT FALSE,
  status       VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/WITHDRAWN',
  published_at DATETIME(3)  NULL,
  created_by   BIGINT       NOT NULL,
  version      BIGINT       NOT NULL DEFAULT 0,
  created_at   DATETIME(3)  NOT NULL,
  updated_at   DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  KEY idx_notice_status (status, pinned, published_at),
  KEY idx_notice_created_by (created_by),
  CONSTRAINT fk_notice_creator FOREIGN KEY (created_by) REFERENCES app_user (id) ON DELETE RESTRICT,
  CONSTRAINT ck_notice_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'WITHDRAWN'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '系统公告，不接收不受控 HTML';

CREATE TABLE audit_log (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  actor_id    BIGINT       NULL COMMENT '系统操作为空',
  action      VARCHAR(64)  NOT NULL,
  target_type VARCHAR(32)  NOT NULL,
  target_id   VARCHAR(64)  NOT NULL,
  trace_id    VARCHAR(64)  NOT NULL,
  summary     JSON         NULL COMMENT '脱敏摘要，禁止密码/令牌/现场码',
  created_at  DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  KEY idx_audit_target (target_type, target_id, created_at),
  KEY idx_audit_actor (actor_id, created_at),
  CONSTRAINT fk_audit_actor FOREIGN KEY (actor_id) REFERENCES app_user (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '审计日志，管理事务失败即回滚';

CREATE TABLE command_dedup (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  user_id      BIGINT       NOT NULL,
  command_type VARCHAR(32)  NOT NULL,
  request_key  VARCHAR(64)  NOT NULL COMMENT '客户端一次点击一个 UUID',
  request_hash CHAR(64)     NOT NULL COMMENT '请求参数 SHA-256',
  result_type  VARCHAR(32)  NOT NULL,
  result_id    VARCHAR(64)  NOT NULL,
  created_at   DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_command_dedup_key (user_id, command_type, request_key),
  KEY idx_command_dedup_user (user_id),
  CONSTRAINT fk_command_dedup_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '写命令幂等记录，保留至少 7 天';

CREATE TABLE settlement_result (
  reservation_id      BIGINT       NOT NULL,
  terminal_event_key  VARCHAR(100) NOT NULL,
  reason              VARCHAR(32)  NOT NULL,
  finished_at         DATETIME(3)  NOT NULL,
  eligible_seconds    BIGINT       NOT NULL DEFAULT 0 COMMENT '有效在座秒数',
  reward_requested    INT          NOT NULL DEFAULT 0,
  reward_actual       INT          NOT NULL DEFAULT 0,
  credit_log_id       BIGINT       NULL,
  created_at          DATETIME(3)  NOT NULL,
  PRIMARY KEY (reservation_id),
  UNIQUE KEY uk_settlement_event_key (terminal_event_key),
  KEY idx_settlement_credit_log (credit_log_id),
  CONSTRAINT fk_settlement_res FOREIGN KEY (reservation_id) REFERENCES reservation (id) ON DELETE RESTRICT,
  CONSTRAINT fk_settlement_credit_log FOREIGN KEY (credit_log_id) REFERENCES credit_log (id) ON DELETE RESTRICT,
  CONSTRAINT ck_settlement_reason CHECK (reason IN ('STUDENT_CANCEL', 'STUDENT_CHECKOUT', 'AUTO_END', 'NO_SHOW', 'LEAVE_TIMEOUT', 'ROOM_CLOSED', 'SEAT_MAINTENANCE', 'HOURS_CHANGED', 'ACCOUNT_DISABLED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '终态结算结果，幂等签退从此表重放';

CREATE TABLE resource_status_event (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  resource_type  VARCHAR(16)  NOT NULL COMMENT 'ROOM/SEAT',
  event_type     VARCHAR(32)  NOT NULL COMMENT 'CREATED/STATUS_CHANGED/HOURS_CHANGED',
  room_id        BIGINT       NOT NULL,
  seat_id        BIGINT       NULL,
  after_snapshot JSON         NOT NULL,
  order_policy   VARCHAR(16)  NULL COMMENT 'KEEP_ACTIVE/FORCE_END',
  effective_at   DATETIME(3)  NOT NULL,
  operator_id    BIGINT       NULL,
  created_at     DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  KEY idx_rse_room (room_id, effective_at, id),
  KEY idx_rse_seat (seat_id, effective_at, id),
  KEY idx_rse_operator (operator_id),
  CONSTRAINT fk_rse_room FOREIGN KEY (room_id) REFERENCES study_room (id) ON DELETE RESTRICT,
  CONSTRAINT fk_rse_seat FOREIGN KEY (seat_id) REFERENCES seat (id) ON DELETE RESTRICT,
  CONSTRAINT fk_rse_operator FOREIGN KEY (operator_id) REFERENCES app_user (id) ON DELETE RESTRICT,
  CONSTRAINT ck_rse_resource_type CHECK (resource_type IN ('ROOM', 'SEAT')),
  CONSTRAINT ck_rse_event_type CHECK (event_type IN ('CREATED', 'STATUS_CHANGED', 'HOURS_CHANGED')),
  CONSTRAINT ck_rse_room_seat CHECK ((resource_type = 'ROOM' AND seat_id IS NULL) OR (resource_type = 'SEAT' AND seat_id IS NOT NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '资源状态事件，按 effective_at,id 重建历史';

CREATE TABLE retained_service (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  reservation_id BIGINT       NOT NULL,
  seat_id        BIGINT       NOT NULL,
  from_at        DATETIME(3)  NOT NULL,
  to_at          DATETIME(3)  NOT NULL,
  cause_event_id BIGINT       NOT NULL,
  created_at     DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_retained_res_cause (reservation_id, cause_event_id),
  KEY idx_retained_seat (seat_id, from_at, to_at),
  CONSTRAINT fk_retained_res FOREIGN KEY (reservation_id) REFERENCES reservation (id) ON DELETE RESTRICT,
  CONSTRAINT fk_retained_seat FOREIGN KEY (seat_id) REFERENCES seat (id) ON DELETE RESTRICT,
  CONSTRAINT fk_retained_cause FOREIGN KEY (cause_event_id) REFERENCES resource_status_event (id) ON DELETE RESTRICT,
  CONSTRAINT ck_retained_range CHECK (from_at < to_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '资源变更保留使用权区间';
