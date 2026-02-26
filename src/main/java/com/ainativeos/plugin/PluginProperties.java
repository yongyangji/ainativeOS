package com.ainativeos.plugin;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ainativeos.plugins")
public class PluginProperties {

    private boolean enabled = true;
    private String manifestDir = "plugins";
    private String highRiskApprovalToken = "ALLOW_HIGH_RISK";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getManifestDir() {
        return manifestDir;
    }

    public void setManifestDir(String manifestDir) {
        this.manifestDir = manifestDir;
    }

    public String getHighRiskApprovalToken() {
        return highRiskApprovalToken;
    }

    public void setHighRiskApprovalToken(String highRiskApprovalToken) {
        this.highRiskApprovalToken = highRiskApprovalToken;
    }
}
