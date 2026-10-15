package edu.campus.studyroom.seed;

import edu.campus.studyroom.common.BusinessException;
import edu.campus.studyroom.security.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Seed CLI entry point. Flags:
 * <ul>
 *   <li>{@code --seed-reference-data} — idempotent reference data (rooms, seats,
 *       fictional students, credit accounts, admin when the env provides the
 *       bootstrap password).</li>
 *   <li>{@code --allow-demo-fixtures} — demo business fixtures; only honoured on
 *       the local/test profiles with {@code app.allow-demo-fixtures=true};
 *       other profiles are refused with an explicit log.</li>
 * </ul>
 * Both flags may appear together (reference data first, fixtures after); with
 * no flag the runner returns immediately and the application starts normally.
 */
@Component
public class SeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);
    private static final String REFERENCE_OPTION = "seed-reference-data";
    private static final String DEMO_OPTION = "allow-demo-fixtures";

    private final SeedReferenceDataService seedReferenceDataService;
    private final DemoFixtureService demoFixtureService;
    private final AppProperties appProperties;
    private final Environment environment;

    public SeedRunner(SeedReferenceDataService seedReferenceDataService,
                      DemoFixtureService demoFixtureService, AppProperties appProperties,
                      Environment environment) {
        this.seedReferenceDataService = seedReferenceDataService;
        this.demoFixtureService = demoFixtureService;
        this.appProperties = appProperties;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean reference = args.containsOption(REFERENCE_OPTION);
        boolean demo = args.containsOption(DEMO_OPTION);
        if (!reference && !demo) {
            return;
        }
        if (reference) {
            try {
                SeedReport report = seedReferenceDataService.seed();
                log.info("--{} 执行完成：{}", REFERENCE_OPTION, report.detail());
            } catch (BusinessException ex) {
                log.error("--{} 执行失败：{}", REFERENCE_OPTION, ex.getMessage());
                throw ex;
            }
        }
        if (demo) {
            if (!demoAllowed()) {
                return;
            }
            try {
                SeedReport report = demoFixtureService.importFixtures();
                log.info("--{} 执行完成：{}", DEMO_OPTION, report.detail());
            } catch (BusinessException ex) {
                log.error("--{} 执行失败，事务已回滚：{}", DEMO_OPTION, ex.getMessage());
                throw ex;
            }
        }
    }

    /**
     * Demo fixtures require both the local/test profile and the explicit
     * {@code app.allow-demo-fixtures=true} switch; anything else is refused.
     */
    private boolean demoAllowed() {
        if (!appProperties.isAllowDemoFixtures()) {
            log.error("拒绝执行演示夹具：app.allow-demo-fixtures=false"
                    + "（仅 local/test profile 通过 application-local/test.yml 开启）");
            return false;
        }
        if (!environment.acceptsProfiles(Profiles.of("local", "test"))) {
            log.error("拒绝执行演示夹具：当前激活 profile [{}] 不是 local/test，"
                            + "正式环境禁止导入演示数据",
                    String.join(",", environment.getActiveProfiles()));
            return false;
        }
        return true;
    }
}
