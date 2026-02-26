package com.ainativeos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "ainativeos.execution")
/**
 * 执行策略配置。
 * <p>
 * 用于统一控制默认重试次数、失败回滚与默认超时。
 */
public class ExecutionPolicyProperties {

    private int defaultMaxRetries = 2;
    private boolean rollbackOnFailure = true;
    private int defaultOpTimeoutSeconds = 60;
    private int circuitBreakerFailureThreshold = 3;
    private int circuitBreakerOpenSeconds = 60;
    private int rateLimitPerMinute = 120;
    private Map<String, ProfilePolicy> profiles = new HashMap<>();

    public int getDefaultMaxRetries() {
        return defaultMaxRetries;
    }

    public void setDefaultMaxRetries(int defaultMaxRetries) {
        this.defaultMaxRetries = defaultMaxRetries;
    }

    public boolean isRollbackOnFailure() {
        return rollbackOnFailure;
    }

    public void setRollbackOnFailure(boolean rollbackOnFailure) {
        this.rollbackOnFailure = rollbackOnFailure;
    }

    public int getDefaultOpTimeoutSeconds() {
        return defaultOpTimeoutSeconds;
    }

    public void setDefaultOpTimeoutSeconds(int defaultOpTimeoutSeconds) {
        this.defaultOpTimeoutSeconds = defaultOpTimeoutSeconds;
    }

    public int getCircuitBreakerFailureThreshold() {
        return circuitBreakerFailureThreshold;
    }

    public void setCircuitBreakerFailureThreshold(int circuitBreakerFailureThreshold) {
        this.circuitBreakerFailureThreshold = circuitBreakerFailureThreshold;
    }

    public int getCircuitBreakerOpenSeconds() {
        return circuitBreakerOpenSeconds;
    }

    public void setCircuitBreakerOpenSeconds(int circuitBreakerOpenSeconds) {
        this.circuitBreakerOpenSeconds = circuitBreakerOpenSeconds;
    }

    public int getRateLimitPerMinute() {
        return rateLimitPerMinute;
    }

    public void setRateLimitPerMinute(int rateLimitPerMinute) {
        this.rateLimitPerMinute = rateLimitPerMinute;
    }

    public Map<String, ProfilePolicy> getProfiles() {
        return profiles;
    }

    public void setProfiles(Map<String, ProfilePolicy> profiles) {
        this.profiles = profiles;
    }

    public ResolvedExecutionPolicy resolveProfile(String profile) {
        String normalized = (profile == null || profile.isBlank()) ? "default" : profile;
        ProfilePolicy defaultProfile = profiles.getOrDefault("default", new ProfilePolicy());
        ProfilePolicy selected = profiles.getOrDefault(normalized, new ProfilePolicy());

        int maxRetries = firstPositive(selected.getMaxRetries(), defaultProfile.getMaxRetries(), defaultMaxRetries);
        int opTimeoutSeconds = firstPositive(selected.getOpTimeoutSeconds(), defaultProfile.getOpTimeoutSeconds(), defaultOpTimeoutSeconds);
        boolean rollback = selected.getRollbackOnFailure() != null
                ? selected.getRollbackOnFailure()
                : (defaultProfile.getRollbackOnFailure() != null ? defaultProfile.getRollbackOnFailure() : rollbackOnFailure);
        int cbThreshold = firstPositive(selected.getCircuitBreakerFailureThreshold(), defaultProfile.getCircuitBreakerFailureThreshold(), circuitBreakerFailureThreshold);
        int cbOpenSeconds = firstPositive(selected.getCircuitBreakerOpenSeconds(), defaultProfile.getCircuitBreakerOpenSeconds(), circuitBreakerOpenSeconds);
        int rateLimit = firstPositive(selected.getRateLimitPerMinute(), defaultProfile.getRateLimitPerMinute(), rateLimitPerMinute);
        return new ResolvedExecutionPolicy(normalized, maxRetries, rollback, opTimeoutSeconds, cbThreshold, cbOpenSeconds, rateLimit);
    }

    private int firstPositive(Integer first, Integer second, int fallback) {
        if (first != null && first > 0) {
            return first;
        }
        if (second != null && second > 0) {
            return second;
        }
        return fallback;
    }

    public static class ProfilePolicy {
        private Integer maxRetries;
        private Boolean rollbackOnFailure;
        private Integer opTimeoutSeconds;
        private Integer circuitBreakerFailureThreshold;
        private Integer circuitBreakerOpenSeconds;
        private Integer rateLimitPerMinute;

        public Integer getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
        }

        public Boolean getRollbackOnFailure() {
            return rollbackOnFailure;
        }

        public void setRollbackOnFailure(Boolean rollbackOnFailure) {
            this.rollbackOnFailure = rollbackOnFailure;
        }

        public Integer getOpTimeoutSeconds() {
            return opTimeoutSeconds;
        }

        public void setOpTimeoutSeconds(Integer opTimeoutSeconds) {
            this.opTimeoutSeconds = opTimeoutSeconds;
        }

        public Integer getCircuitBreakerFailureThreshold() {
            return circuitBreakerFailureThreshold;
        }

        public void setCircuitBreakerFailureThreshold(Integer circuitBreakerFailureThreshold) {
            this.circuitBreakerFailureThreshold = circuitBreakerFailureThreshold;
        }

        public Integer getCircuitBreakerOpenSeconds() {
            return circuitBreakerOpenSeconds;
        }

        public void setCircuitBreakerOpenSeconds(Integer circuitBreakerOpenSeconds) {
            this.circuitBreakerOpenSeconds = circuitBreakerOpenSeconds;
        }

        public Integer getRateLimitPerMinute() {
            return rateLimitPerMinute;
        }

        public void setRateLimitPerMinute(Integer rateLimitPerMinute) {
            this.rateLimitPerMinute = rateLimitPerMinute;
        }
    }

    public record ResolvedExecutionPolicy(
            String profile,
            int maxRetries,
            boolean rollbackOnFailure,
            int opTimeoutSeconds,
            int circuitBreakerFailureThreshold,
            int circuitBreakerOpenSeconds,
            int rateLimitPerMinute
    ) {
    }
}
