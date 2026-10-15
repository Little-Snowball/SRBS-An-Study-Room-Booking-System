package edu.campus.studyroom.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed application properties under the "app" prefix.
 * Secrets only ever come from environment variables, never from Git.
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String businessZone;
    private String jwtSecretBase64;
    private String uploadDir;
    private String bootstrapAccount;
    private String bootstrapPassword;
    private boolean allowDemoFixtures;
    private Session session = new Session();
    private RateLimit rateLimit = new RateLimit();

    public String getBusinessZone() {
        return businessZone;
    }

    public void setBusinessZone(String businessZone) {
        this.businessZone = businessZone;
    }

    public String getJwtSecretBase64() {
        return jwtSecretBase64;
    }

    public void setJwtSecretBase64(String jwtSecretBase64) {
        this.jwtSecretBase64 = jwtSecretBase64;
    }

    public String getUploadDir() {
        return uploadDir;
    }

    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }

    public String getBootstrapAccount() {
        return bootstrapAccount;
    }

    public void setBootstrapAccount(String bootstrapAccount) {
        this.bootstrapAccount = bootstrapAccount;
    }

    public String getBootstrapPassword() {
        return bootstrapPassword;
    }

    public void setBootstrapPassword(String bootstrapPassword) {
        this.bootstrapPassword = bootstrapPassword;
    }

    public boolean isAllowDemoFixtures() {
        return allowDemoFixtures;
    }

    public void setAllowDemoFixtures(boolean allowDemoFixtures) {
        this.allowDemoFixtures = allowDemoFixtures;
    }

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimit rateLimit) {
        this.rateLimit = rateLimit;
    }

    public static class Session {
        private int accessTokenHours = 2;
        private int maxSessionsPerUser = 5;
        private int expiredRetentionDays = 7;

        public int getAccessTokenHours() {
            return accessTokenHours;
        }

        public void setAccessTokenHours(int accessTokenHours) {
            this.accessTokenHours = accessTokenHours;
        }

        public int getMaxSessionsPerUser() {
            return maxSessionsPerUser;
        }

        public void setMaxSessionsPerUser(int maxSessionsPerUser) {
            this.maxSessionsPerUser = maxSessionsPerUser;
        }

        public int getExpiredRetentionDays() {
            return expiredRetentionDays;
        }

        public void setExpiredRetentionDays(int expiredRetentionDays) {
            this.expiredRetentionDays = expiredRetentionDays;
        }
    }

    public static class RateLimit {
        private int loginPerIpPerMinute = 20;
        private int loginFailPerAccount15min = 5;
        private int loginPauseMinutes = 15;
        private int checkinPerOrder5min = 5;
        private int checkinPerIpPerMinute = 30;

        public int getLoginPerIpPerMinute() {
            return loginPerIpPerMinute;
        }

        public void setLoginPerIpPerMinute(int loginPerIpPerMinute) {
            this.loginPerIpPerMinute = loginPerIpPerMinute;
        }

        public int getLoginFailPerAccount15min() {
            return loginFailPerAccount15min;
        }

        public void setLoginFailPerAccount15min(int loginFailPerAccount15min) {
            this.loginFailPerAccount15min = loginFailPerAccount15min;
        }

        public int getLoginPauseMinutes() {
            return loginPauseMinutes;
        }

        public void setLoginPauseMinutes(int loginPauseMinutes) {
            this.loginPauseMinutes = loginPauseMinutes;
        }

        public int getCheckinPerOrder5min() {
            return checkinPerOrder5min;
        }

        public void setCheckinPerOrder5min(int checkinPerOrder5min) {
            this.checkinPerOrder5min = checkinPerOrder5min;
        }

        public int getCheckinPerIpPerMinute() {
            return checkinPerIpPerMinute;
        }

        public void setCheckinPerIpPerMinute(int checkinPerIpPerMinute) {
            this.checkinPerIpPerMinute = checkinPerIpPerMinute;
        }
    }
}
