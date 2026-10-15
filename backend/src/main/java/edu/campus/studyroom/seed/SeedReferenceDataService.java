package edu.campus.studyroom.seed;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.campus.studyroom.audit.AuditService;
import edu.campus.studyroom.common.BusinessClock;
import edu.campus.studyroom.common.BusinessException;
import edu.campus.studyroom.common.ErrorCode;
import edu.campus.studyroom.credit.domain.CreditAccount;
import edu.campus.studyroom.credit.mapper.CreditAccountMapper;
import edu.campus.studyroom.resource.domain.ResourceEventType;
import edu.campus.studyroom.resource.domain.ResourceStatusEvent;
import edu.campus.studyroom.resource.domain.ResourceType;
import edu.campus.studyroom.resource.domain.RoomStatus;
import edu.campus.studyroom.resource.domain.Seat;
import edu.campus.studyroom.resource.domain.SeatStatus;
import edu.campus.studyroom.resource.domain.StudyRoom;
import edu.campus.studyroom.resource.mapper.ResourceStatusEventMapper;
import edu.campus.studyroom.resource.mapper.SeatMapper;
import edu.campus.studyroom.resource.mapper.StudyRoomMapper;
import edu.campus.studyroom.rule.BusinessGuardLock;
import edu.campus.studyroom.security.AppProperties;
import edu.campus.studyroom.security.PasswordPolicy;
import edu.campus.studyroom.user.domain.AppUser;
import edu.campus.studyroom.user.domain.UserRole;
import edu.campus.studyroom.user.domain.UserStatus;
import edu.campus.studyroom.user.mapper.AppUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Controlled-CLI reference data seed (empty database, reproducible init).
 * Writes rooms/seats/users/credit accounts directly through the mappers inside
 * one guarded READ_COMMITTED transaction. Idempotent: existing rows are
 * detected by natural key (room code / account) and skipped, never overwritten.
 * The admin password only ever comes from the environment; nothing sensitive
 * is written to logs.
 */
@Service
public class SeedReferenceDataService {

    private static final Logger log = LoggerFactory.getLogger(SeedReferenceDataService.class);

    private static final LocalTime OPEN_TIME = LocalTime.of(7, 0);
    private static final LocalTime CLOSE_TIME = LocalTime.of(22, 30);
    private static final int ROWS = 4;
    private static final int COLS = 6;

    /** 演示现场码（仅参考数据用途，哈希后入库，不明文落库）。 */
    private static final String DEMO_CHECKIN_CODE = "123456";
    /** 虚构学生的固定演示初始密码（参考数据用途，首次登录后应自行修改）。 */
    private static final String STUDENT_DEMO_PASSWORD = "Study@2024demo";
    private static final String ADMIN_ACCOUNT = "ADMIN";
    private static final String ADMIN_NAME = "系统管理员";
    private static final String ADMIN_COLLEGE = "信息中心";

    /** 座位空洞（行,列，1 起）：不生成座位的坐标。 */
    private static final Set<String> SEAT_HOLES = Set.of("4-5", "4-6");
    /** 维护中座位坐标（每房间至少 2 个）。 */
    private static final Set<String> MAINTENANCE_CELLS = Set.of("1-1", "4-1");

    private record RoomSpec(String code, String name, String campus, String building,
                            int floorNo, String seatPrefix, RoomStatus status) {
    }

    private record StudentSpec(String account, String name, String college, int score, UserStatus status) {
    }

    private static final List<RoomSpec> ROOM_SPECS = List.of(
            new RoomSpec("SR-A101", "中心图书馆101自习室", "中心校区", "图书馆", 1, "A", RoomStatus.OPEN),
            new RoomSpec("SR-B202", "中心图书馆202自习室", "中心校区", "图书馆", 2, "B", RoomStatus.OPEN),
            new RoomSpec("SR-C303", "信息楼303研讨自习室", "东校区", "信息楼", 3, "C", RoomStatus.CLOSED));

    private static final List<StudentSpec> STUDENT_SPECS = List.of(
            new StudentSpec("20230001", "林晓", "计算机学院", 100, UserStatus.ACTIVE),
            new StudentSpec("20230002", "陈思远", "计算机学院", 100, UserStatus.ACTIVE),
            new StudentSpec("20230003", "王一鸣", "外国语学院", 60, UserStatus.ACTIVE),
            new StudentSpec("20230004", "李梓涵", "经济管理学院", 59, UserStatus.ACTIVE),
            new StudentSpec("20230005", "赵子龙", "理学院", 5, UserStatus.ACTIVE),
            new StudentSpec("20230006", "刘诗涵", "人文学院", 100, UserStatus.ACTIVE),
            new StudentSpec("20230007", "孙嘉怡", "机械工程学院", 100, UserStatus.ACTIVE),
            new StudentSpec("20230008", "周文昊", "马克思主义学院", 100, UserStatus.DISABLED));

    private final BusinessGuardLock businessGuardLock;
    private final StudyRoomMapper studyRoomMapper;
    private final SeatMapper seatMapper;
    private final ResourceStatusEventMapper resourceStatusEventMapper;
    private final AppUserMapper appUserMapper;
    private final CreditAccountMapper creditAccountMapper;
    private final PasswordPolicy passwordPolicy;
    private final AppProperties appProperties;
    private final AuditService auditService;
    private final BusinessClock clock;
    private final ObjectMapper objectMapper;

    public SeedReferenceDataService(BusinessGuardLock businessGuardLock, StudyRoomMapper studyRoomMapper,
                                    SeatMapper seatMapper, ResourceStatusEventMapper resourceStatusEventMapper,
                                    AppUserMapper appUserMapper, CreditAccountMapper creditAccountMapper,
                                    PasswordPolicy passwordPolicy, AppProperties appProperties,
                                    AuditService auditService, BusinessClock clock,
                                    ObjectMapper objectMapper) {
        this.businessGuardLock = businessGuardLock;
        this.studyRoomMapper = studyRoomMapper;
        this.seatMapper = seatMapper;
        this.resourceStatusEventMapper = resourceStatusEventMapper;
        this.appUserMapper = appUserMapper;
        this.creditAccountMapper = creditAccountMapper;
        this.passwordPolicy = passwordPolicy;
        this.appProperties = appProperties;
        this.auditService = auditService;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    /**
     * Seeds rooms, seats, fictional students and (when the environment provides
     * BOOTSTRAP_PASSWORD) one admin. Everything is idempotent by natural key.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SeedReport seed() {
        businessGuardLock.lock();
        LocalDateTime now = clock.nowLocal();
        String checkinCodeHash = passwordPolicy.hash(DEMO_CHECKIN_CODE);

        int roomsCreated = 0;
        int roomsSkipped = 0;
        int seatsCreated = 0;
        int seatsSkipped = 0;
        int eventsCreated = 0;
        int usersCreated = 0;
        int usersSkipped = 0;
        int creditsCreated = 0;
        int creditsSkipped = 0;
        int adminsCreated = 0;
        int adminsSkipped = 0;

        for (RoomSpec spec : ROOM_SPECS) {
            StudyRoom existing = studyRoomMapper.selectByCode(spec.code());
            Long roomId;
            if (existing != null) {
                roomsSkipped++;
                roomId = existing.getId();
                log.info("参考数据跳过：房间已存在 code={} id={}", spec.code(), roomId);
            } else {
                StudyRoom room = new StudyRoom();
                room.setCode(spec.code());
                room.setName(spec.name());
                room.setCampus(spec.campus());
                room.setBuilding(spec.building());
                room.setFloorNo(spec.floorNo());
                room.setOpenTime(OPEN_TIME);
                room.setCloseTime(CLOSE_TIME);
                room.setRowsCount(ROWS);
                room.setColsCount(COLS);
                room.setCheckinCodeHash(checkinCodeHash);
                room.setStatus(spec.status());
                room.setVersion(0L);
                room.setCreatedAt(now);
                room.setUpdatedAt(now);
                studyRoomMapper.insert(room);
                roomId = room.getId();
                roomsCreated++;

                ResourceStatusEvent event = new ResourceStatusEvent();
                event.setResourceType(ResourceType.ROOM);
                event.setEventType(ResourceEventType.CREATED);
                event.setRoomId(roomId);
                event.setSeatId(null);
                event.setAfterSnapshot(roomSnapshot(room));
                event.setOrderPolicy(null);
                event.setEffectiveAt(now);
                event.setOperatorId(null);
                event.setCreatedAt(now);
                resourceStatusEventMapper.insert(event);
                eventsCreated++;
                log.info("参考数据创建：房间 code={} id={} status={}", spec.code(), roomId, spec.status());
            }

            List<Seat> batch = new ArrayList<>();
            int roomSeatsSkipped = 0;
            for (int row = 1; row <= ROWS; row++) {
                for (int col = 1; col <= COLS; col++) {
                    if (SEAT_HOLES.contains(row + "-" + col)) {
                        continue;
                    }
                    if (seatMapper.selectByRoomRowCol(roomId, row, col) != null) {
                        roomSeatsSkipped++;
                        continue;
                    }
                    Seat seat = new Seat();
                    seat.setRoomId(roomId);
                    seat.setCode(spec.seatPrefix() + "-R" + String.format("%02d", row)
                            + "-C" + String.format("%02d", col));
                    seat.setRowNo(row);
                    seat.setColNo(col);
                    seat.setHasPower((row + col) % 2 == 0);
                    seat.setNearWindow(row == 1);
                    seat.setQuietZone(row >= 3);
                    seat.setStatus(MAINTENANCE_CELLS.contains(row + "-" + col)
                            ? SeatStatus.MAINTENANCE : SeatStatus.NORMAL);
                    seat.setVersion(0L);
                    seat.setCreatedAt(now);
                    seat.setUpdatedAt(now);
                    batch.add(seat);
                }
            }
            if (!batch.isEmpty()) {
                seatMapper.insertBatch(batch);
            }
            seatsCreated += batch.size();
            seatsSkipped += roomSeatsSkipped;
            if (!batch.isEmpty()) {
                log.info("参考数据创建：房间 {} 座位 {} 个（跳过 {}/维护中按规格）",
                        spec.code(), batch.size(), roomSeatsSkipped);
            }
        }

        for (StudentSpec spec : STUDENT_SPECS) {
            AppUser existing = appUserMapper.selectByAccount(spec.account());
            Long userId;
            if (existing != null) {
                usersSkipped++;
                userId = existing.getId();
                log.info("参考数据跳过：学生已存在 account={}", spec.account());
            } else {
                AppUser user = new AppUser();
                user.setAccount(spec.account());
                user.setPasswordHash(passwordPolicy.hash(STUDENT_DEMO_PASSWORD));
                user.setName(spec.name());
                user.setCollege(spec.college());
                user.setRole(UserRole.STUDENT);
                user.setStatus(spec.status());
                user.setBlocked(false);
                user.setMustChangePassword(false);
                user.setAuthVersion(0);
                user.setCreatedAt(now);
                user.setUpdatedAt(now);
                appUserMapper.insert(user);
                userId = user.getId();
                usersCreated++;
                log.info("参考数据创建：学生 account={} name={} college={} score={} status={}",
                        spec.account(), spec.name(), spec.college(), spec.score(), spec.status());
            }
            if (creditAccountMapper.selectByUserId(userId) == null) {
                CreditAccount account = new CreditAccount();
                account.setUserId(userId);
                account.setScore(spec.score());
                account.setVersion(0L);
                account.setUpdatedAt(now);
                creditAccountMapper.insert(account);
                creditsCreated++;
            } else {
                creditsSkipped++;
                log.info("参考数据跳过：信用账户已存在 account={}", spec.account());
            }
        }

        AppUser existingAdmin = appUserMapper.selectByAccount(ADMIN_ACCOUNT);
        if (existingAdmin != null) {
            adminsSkipped++;
            log.info("参考数据跳过：管理员已存在 account={}", ADMIN_ACCOUNT);
        } else {
            String bootstrapPassword = appProperties.getBootstrapPassword();
            if (bootstrapPassword == null || bootstrapPassword.isBlank()) {
                log.warn("参考数据跳过：未设置 BOOTSTRAP_PASSWORD 环境变量，无法创建管理员 {}；"
                        + "请改用 --bootstrap-admin 或设置环境变量后重试", ADMIN_ACCOUNT);
            } else {
                AppUser admin = new AppUser();
                admin.setAccount(ADMIN_ACCOUNT);
                admin.setPasswordHash(passwordPolicy.hash(bootstrapPassword));
                admin.setName(ADMIN_NAME);
                admin.setCollege(ADMIN_COLLEGE);
                admin.setRole(UserRole.ADMIN);
                admin.setStatus(UserStatus.ACTIVE);
                admin.setBlocked(false);
                admin.setMustChangePassword(true);
                admin.setAuthVersion(0);
                admin.setCreatedAt(now);
                admin.setUpdatedAt(now);
                appUserMapper.insert(admin);
                adminsCreated++;
                log.info("参考数据创建：管理员 account={}（初始密码来自环境变量，首次登录强制修改）",
                        ADMIN_ACCOUNT);
            }
        }

        int created = roomsCreated + seatsCreated + eventsCreated + usersCreated + creditsCreated + adminsCreated;
        int skipped = roomsSkipped + seatsSkipped + usersSkipped + creditsSkipped + adminsSkipped;
        String detail = String.format(
                "房间 %d/%d，座位 %d/%d，资源事件 %d，用户 %d/%d，信用账户 %d/%d，管理员 %d/%d",
                roomsCreated, roomsSkipped, seatsCreated, seatsSkipped, eventsCreated,
                usersCreated, usersSkipped, creditsCreated, creditsSkipped, adminsCreated, adminsSkipped);
        auditService.record((Long) null, "SEED_REFERENCE_DATA", "SYSTEM", "SEED_REFERENCE_DATA",
                summaryJson(roomsCreated, seatsCreated, usersCreated, creditsCreated, adminsCreated));
        log.info("参考数据导入完成：{}", detail);
        return new SeedReport(created, skipped, detail);
    }

    private String roomSnapshot(StudyRoom room) {
        try {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("code", room.getCode());
            snapshot.put("name", room.getName());
            snapshot.put("campus", room.getCampus());
            snapshot.put("building", room.getBuilding());
            snapshot.put("floorNo", room.getFloorNo());
            snapshot.put("openTime", room.getOpenTime().toString());
            snapshot.put("closeTime", room.getCloseTime().toString());
            snapshot.put("rowsCount", room.getRowsCount());
            snapshot.put("colsCount", room.getColsCount());
            snapshot.put("status", room.getStatus().name());
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "房间快照序列化失败");
        }
    }

    private String summaryJson(int rooms, int seats, int users, int credits, int admins) {
        try {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("rooms", rooms);
            summary.put("seats", seats);
            summary.put("users", users);
            summary.put("creditAccounts", credits);
            summary.put("admins", admins);
            return objectMapper.writeValueAsString(summary);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "审计摘要序列化失败");
        }
    }
}
