package edu.campus.studyroom.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Provides the injectable clock. Production uses UTC system time;
 * tests replace this bean with a fixed/mutable clock.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public BusinessClock businessClock(Clock clock,
                                       @org.springframework.beans.factory.annotation.Value("${app.business-zone:Asia/Shanghai}")
                                       String zone) {
        return new BusinessClock(clock, java.time.ZoneId.of(zone));
    }
}
