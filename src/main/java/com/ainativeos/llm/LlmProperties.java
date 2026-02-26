package com.ainativeos.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM 配置。
 */
@ConfigurationProperties(prefix = "ainativeos.llm")
public class LlmProperties {

    private boolean enabled = false;
    private String provider = "openai";
    private String endpoint = "https://api.openai.com/v1/chat/completions";
    private String apiKey = "";
    private String model = "gpt-4o-mini";
    private String fallbackProvider = "";
    private String fallbackEndpoint = "";
    private String fallbackApiKey = "";
    private String fallbackModel = "";
    private int timeoutSeconds = 20;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getFallbackProvider() {
        return fallbackProvider;
    }

    public void setFallbackProvider(String fallbackProvider) {
        this.fallbackProvider = fallbackProvider;
    }

    public String getFallbackEndpoint() {
        return fallbackEndpoint;
    }

    public void setFallbackEndpoint(String fallbackEndpoint) {
        this.fallbackEndpoint = fallbackEndpoint;
    }

    public String getFallbackApiKey() {
        return fallbackApiKey;
    }

    public void setFallbackApiKey(String fallbackApiKey) {
        this.fallbackApiKey = fallbackApiKey;
    }

    public String getFallbackModel() {
        return fallbackModel;
    }

    public void setFallbackModel(String fallbackModel) {
        this.fallbackModel = fallbackModel;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
