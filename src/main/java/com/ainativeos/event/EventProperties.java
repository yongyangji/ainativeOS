package com.ainativeos.event;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "ainativeos.events")
public class EventProperties {

    private boolean enabled = false;
    private List<String> webhookUrls = new ArrayList<>();
    private int timeoutSeconds = 5;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getWebhookUrls() {
        return webhookUrls;
    }

    public void setWebhookUrls(List<String> webhookUrls) {
        this.webhookUrls = webhookUrls;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
