package edu.campus.studyroom.auth.bootstrap;

import edu.campus.studyroom.audit.AuditService;
import edu.campus.studyroom.common.BusinessClock;
import edu.campus.studyroom.credit.domain.CreditAccount;
import edu.campus.studyroom.credit.mapper.CreditAccountMapper;
import edu.campus.studyroom.security.AppProperties;
import edu.campus.studyroom.security.PasswordPolicy;
import edu.campus.studyroom.user.domain.AppUser;
import edu.campus.studyroom.user.domain.UserRole;
import edu.campus.studyroom.user.domain.UserStatus;
import edu.campus.studyroom.user.mapper.AppUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * One-shot administrator bootstrap CLI. Active only with the
 * "--bootstrap-admin" flag; the account and the initial password must come from
 * the environment (BOOTSTRAP_ACCOUNT / BOOTSTRAP_PASSWORD) — no default
 * password is ever generated or persisted. Refuses to run when an admin
 * already exists, writes app_user + credit_account + audit_log in a single
 * transaction, then exits the JVM without serving traffic.
 */
@Component
public class BootstrapAdminRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);
    private static final String BOOTSTRAP_FLAG = "--bootstrap-admin";
    private static final String BOOTSTRAP_OPTION = "bootstrap-admin";
    private static final String DEFAULT_ADMIN_NAME = "系统管理员";

    private final ApplicationContext applicationContext;
    private final AppProperties appProperties;
    private final AppUserMapper appUserMapper;
    private final CreditAccountMapper creditAccountMapper;
    private final PasswordPolicy passwordPolicy;
    private final AuditService auditService;
    private final BusinessClock clock;
    private final TransactionTemplate transactionTemplate;

    public BootstrapAdminRunner(ApplicationContext applicationContext, AppProperties appProperties,
                                AppUserMapper appUserMapper, CreditAccountMapper creditAccountMapper,
                                PasswordPolicy passwordPolicy, AuditService auditService,
                                BusinessClock clock, PlatformTransactionManager transactionManager) {
        this.applicationContext = applicationContext;
        this.appProperties = appProperties;
        this.appUserMapper = appUserMapper;
        this.creditAccountMapper = creditAccountMapper;
        this.passwordPolicy = passwordPolicy;
        this.auditService = auditService;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption(BOOTSTRAP_OPTION)) {
            return;
        }
        String account = appProperties.getBootstrapAccount();
        String password = appProperties.getBootstrapPassword();
        if (isBlank(account) || isBlank(password)) {
            log.error("{} 模式需要通过环境变量提供 BOOTSTRAP_ACCOUNT 与 BOOTSTRAP_PASSWORD"
                    + "（对应 app.bootstrap-account / app.bootstrap-password），已拒绝启动", BOOTSTRAP_FLAG);
            exit(1);
            return;
        }
        if (appUserMapper.countByRole(UserRole.ADMIN) > 0) {
            log.info("管理员已存在，拒绝重复初始化");
            exit(0);
            return;
        }
        try {
            transactionTemplate.execute(status -> {
                createAdmin(account.trim(), password);
                return null;
            });
            log.info("管理员初始化完成：account={}；初始密码仅本次由环境变量提供，不会写入日志，"
                    + "请立即登录并修改密码", maskAccount(account.trim()));
        } catch (Exception ex) {
            log.error("管理员初始化失败，事务已回滚", ex);
            exit(1);
            return;
        }
        exit(0);
    }

    private void createAdmin(String account, String password) {
        LocalDateTime now = clock.nowLocal();
        AppUser admin = new AppUser();
        admin.setAccount(account.toUpperCase(Locale.ROOT));
        admin.setPasswordHash(passwordPolicy.hash(password));
        admin.setName(DEFAULT_ADMIN_NAME);
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        admin.setBlocked(false);
        admin.setMustChangePassword(true);
        admin.setAuthVersion(0);
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);
        appUserMapper.insert(admin);

        CreditAccount creditAccount = new CreditAccount();
        creditAccount.setUserId(admin.getId());
        creditAccount.setScore(100);
        creditAccount.setVersion(0L);
        creditAccount.setUpdatedAt(now);
        creditAccountMapper.insert(creditAccount);

        auditService.record((Long) null, "BOOTSTRAP_ADMIN", "APP_USER", String.valueOf(admin.getId()),
                "{\"account\":\"" + maskAccount(admin.getAccount()) + "\"}");
    }

    private void exit(int code) {
        SpringApplication.exit(applicationContext, () -> code);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Keeps the last four characters only; never stores or logs the password. */
    private static String maskAccount(String account) {
        if (isBlank(account)) {
            return "";
        }
        String trimmed = account.trim();
        return trimmed.length() <= 4 ? "****" : "****" + trimmed.substring(trimmed.length() - 4);
    }
}
