package edu.campus.studyroom.integration;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T46 数据库不变量与迁移校验。
 * <p>需要真实 MySQL（本课程不引入 Docker/Testcontainers）。未配置 {@code TEST_DB_PASSWORD}
 * 或连接失败时整个类跳过，纯单测仍照常执行。
 * <p>连接信息只从环境变量读取：TEST_DB_URL / TEST_DB_USER / TEST_DB_PASSWORD，
 * 默认指向 studyroom_test 库，绝不指向开发库。
 */
class SchemaInvariantIT {

    private static final String URL = System.getenv().getOrDefault("TEST_DB_URL",
            "jdbc:mysql://127.0.0.1:3306/studyroom_test?useUnicode=true&characterEncoding=utf8"
                    + "&connectionTimeZone=Asia/Shanghai&serverTimezone=Asia/Shanghai");
    private static final String USER = System.getenv().getOrDefault("TEST_DB_USER", "studyroom_app");
    private static final String PASSWORD = System.getenv().getOrDefault("TEST_DB_PASSWORD", "");

    private static boolean available;

    @BeforeAll
    static void probe() {
        if (PASSWORD.isBlank()) {
            available = false;
            return;
        }
        try (Connection connection = dataSource().getConnection()) {
            available = connection.isValid(3);
        } catch (Exception ex) {
            available = false;
        }
        Assumptions.assumeTrue(available,
                "跳过集成测试：未配置可用的 studyroom_test 连接（TEST_DB_PASSWORD）");
    }

    private static DriverManagerDataSource dataSource() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setUrl(URL);
        ds.setUsername(USER);
        ds.setPassword(PASSWORD);
        return ds;
    }

    private long scalar(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            assertTrue(rs.next(), "查询应返回一行：" + sql);
            return rs.getLong(1);
        }
    }

    @Test
    @DisplayName("迁移已执行：默认规则与 guard 协调行存在")
    void seedRuleAndGuardExist() throws Exception {
        try (Connection connection = dataSource().getConnection()) {
            assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM business_guard WHERE id = 1"));
            assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM rule_version WHERE source = 'SYSTEM_SEED'"));
        }
    }

    @Test
    @DisplayName("不变量：无同座位有效预约重叠")
    void noSeatOverlap() throws Exception {
        try (Connection connection = dataSource().getConnection()) {
            long overlaps = scalar(connection, """
                    SELECT COUNT(*) FROM reservation a JOIN reservation b
                      ON a.seat_id = b.seat_id AND a.id < b.id
                     AND a.start_at < b.end_at AND a.end_at > b.start_at
                    WHERE a.status IN ('RESERVED','IN_USE','LEFT_TEMP')
                      AND b.status IN ('RESERVED','IN_USE','LEFT_TEMP')
                    """);
            assertEquals(0, overlaps, "同座位有效预约不得重叠");
        }
    }

    @Test
    @DisplayName("不变量：无同学生有效预约重叠")
    void noUserOverlap() throws Exception {
        try (Connection connection = dataSource().getConnection()) {
            long overlaps = scalar(connection, """
                    SELECT COUNT(*) FROM reservation a JOIN reservation b
                      ON a.user_id = b.user_id AND a.id < b.id
                     AND a.start_at < b.end_at AND a.end_at > b.start_at
                    WHERE a.status IN ('RESERVED','IN_USE','LEFT_TEMP')
                      AND b.status IN ('RESERVED','IN_USE','LEFT_TEMP')
                    """);
            assertEquals(0, overlaps, "同学生有效预约不得重叠");
        }
    }

    @Test
    @DisplayName("不变量：信用分在 0—100 且流水前后连续")
    void creditInvariants() throws Exception {
        try (Connection connection = dataSource().getConnection()) {
            assertEquals(0, scalar(connection,
                    "SELECT COUNT(*) FROM credit_account WHERE score < 0 OR score > 100"));
            assertEquals(0, scalar(connection,
                    "SELECT COUNT(*) FROM credit_log WHERE balance_after <> balance_before + actual_delta"));
        }
    }

    @Test
    @DisplayName("不变量：每单最多一个终态结算与一个违约记录")
    void settlementUniqueness() throws Exception {
        try (Connection connection = dataSource().getConnection()) {
            assertEquals(0, scalar(connection, """
                    SELECT COUNT(*) FROM (SELECT reservation_id FROM settlement_result
                     GROUP BY reservation_id HAVING COUNT(*) > 1) t
                    """));
            assertEquals(0, scalar(connection, """
                    SELECT COUNT(*) FROM (SELECT reservation_id FROM violation
                     GROUP BY reservation_id HAVING COUNT(*) > 1) t
                    """));
        }
    }

    @Test
    @DisplayName("不变量：现场码哈希不可为明文六位数字")
    void checkinCodeHashed() throws Exception {
        try (Connection connection = dataSource().getConnection()) {
            assertEquals(0, scalar(connection,
                    "SELECT COUNT(*) FROM study_room WHERE checkin_code_hash REGEXP '^[0-9]{6}$'"));
        }
    }
}