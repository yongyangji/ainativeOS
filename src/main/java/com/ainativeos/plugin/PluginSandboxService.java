package com.ainativeos.plugin;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PluginSandboxService {

    private static final Set<String> HIGH_RISK_PREFIXES = Set.of(
            "SYSTEM_",
            "K8S_",
            "CLOUD_",
            "DOCKER_",
            "RUNTIME_"
    );

    private final PluginProperties pluginProperties;

    public PluginSandboxService(PluginProperties pluginProperties) {
        this.pluginProperties = pluginProperties;
    }

    public SandboxDecision check(PluginManifest manifest, Map<String, Object> executionParams) {
        if (!pluginProperties.isEnabled()) {
            return SandboxDecision.deny("Plugin system disabled", "plugin-disabled");
        }
        if (!manifest.enabled()) {
            return SandboxDecision.deny("Plugin is disabled", "plugin-not-enabled");
        }

        boolean highRisk = isHighRisk(manifest.requiredCapabilities());
        if (!highRisk) {
            return SandboxDecision.permit();
        }

        String token = value(executionParams, "pluginApprovalToken");
        if (token == null || !token.equals(pluginProperties.getHighRiskApprovalToken())) {
            return SandboxDecision.deny(
                    "High-risk plugin requires explicit approval token",
                    "plugin-approval-required"
            );
        }
        return SandboxDecision.permit();
    }

    private boolean isHighRisk(List<String> capabilities) {
        if (capabilities == null) {
            return false;
        }
        return capabilities.stream().anyMatch(capability ->
                HIGH_RISK_PREFIXES.stream().anyMatch(capability::startsWith)
        );
    }

    private String value(Map<String, Object> params, String key) {
        Object raw = params.get(key);
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw).trim();
        return value.isBlank() ? null : value;
    }

    public record SandboxDecision(
            boolean allowed,
            String reason,
            String code
    ) {
        public static SandboxDecision permit() {
            return new SandboxDecision(true, "ok", "");
        }

        public static SandboxDecision deny(String reason, String code) {
            return new SandboxDecision(false, reason, code);
        }
    }
}
