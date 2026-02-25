package com.ainativeos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ainativeos.execution")
public class ExecutionPolicyProperties {

    private int defaultMaxRetries = 2;
    private boolean rollbackOnFailure = true;
    private int defaultOpTimeoutSeconds = 60;

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
}
