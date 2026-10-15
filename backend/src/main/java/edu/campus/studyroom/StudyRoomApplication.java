package edu.campus.studyroom;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(edu.campus.studyroom.security.AppProperties.class)
@EnableScheduling
public class StudyRoomApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(StudyRoomApplication.class);
        if (isBootstrapAdminMode(args)) {
            // CLI bootstrap mode: initialize the admin, then exit without serving traffic.
            application.setWebApplicationType(WebApplicationType.NONE);
            application.setBannerMode(Banner.Mode.OFF);
        }
        application.run(args);
    }

    private static boolean isBootstrapAdminMode(String[] args) {
        for (String arg : args) {
            if ("--bootstrap-admin".equals(arg)) {
                return true;
            }
        }
        return false;
    }
}
