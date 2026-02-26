package com.ainativeos.capability.provider;

import com.ainativeos.capability.CapabilityProvider;
import com.ainativeos.domain.AtomicOp;
import com.ainativeos.domain.ContextFrame;
import com.ainativeos.domain.OpExecutionResult;
import com.ainativeos.plugin.PluginManifest;
import com.ainativeos.plugin.PluginRegistryService;
import com.ainativeos.plugin.PluginSandboxService;
import com.ainativeos.runtime.CommandExecutionResult;
import com.ainativeos.runtime.LocalCommandExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class PluginCapabilityProvider implements CapabilityProvider {

    private final PluginRegistryService pluginRegistryService;
    private final PluginSandboxService pluginSandboxService;
    private final LocalCommandExecutor localCommandExecutor;
    private final ObjectMapper objectMapper;

    public PluginCapabilityProvider(
            PluginRegistryService pluginRegistryService,
            PluginSandboxService pluginSandboxService,
            LocalCommandExecutor localCommandExecutor,
            ObjectMapper objectMapper
    ) {
        this.pluginRegistryService = pluginRegistryService;
        this.pluginSandboxService = pluginSandboxService;
        this.localCommandExecutor = localCommandExecutor;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String opType) {
        return opType.startsWith("PLUGIN_");
    }

    @Override
    public String providerName() {
        return "plugin-provider";
    }

    @Override
    public List<String> advertisedOpTypes() {
        return List.of("PLUGIN_EXECUTE");
    }

    @Override
    public Map<String, Object> metadata() {
        return Map.of(
                "manifestDir", "plugins",
                "isolation", "isolated process execution"
        );
    }

    @Override
    public OpExecutionResult execute(AtomicOp atomicOp) {
        String pluginId = value(atomicOp, "pluginId");
        if (pluginId == null) {
            return new OpExecutionResult(
                    false,
                    providerName(),
                    "PLUGIN_EXECUTE missing pluginId",
                    List.of(),
                    "PLUGIN_ID_MISSING",
                    "Provide constraints.pluginId"
            );
        }

        Optional<PluginManifest> manifestOpt = pluginRegistryService.findById(pluginId);
        if (manifestOpt.isEmpty()) {
            return new OpExecutionResult(
                    false,
                    providerName(),
                    "Plugin not found: " + pluginId,
                    List.of(),
                    "PLUGIN_NOT_FOUND",
                    "Register plugin manifest under plugins/*.json"
            );
        }
        PluginManifest manifest = manifestOpt.get();
        PluginSandboxService.SandboxDecision decision = pluginSandboxService.check(manifest, atomicOp.parameters());
        if (!decision.allowed()) {
            return new OpExecutionResult(
                    false,
                    providerName(),
                    "Plugin sandbox denied: " + decision.reason(),
                    List.of(),
                    decision.code(),
                    "Add explicit approval token or reduce requiredCapabilities"
            );
        }

        String inputJson = "{}";
        try {
            Object explicitJson = atomicOp.parameters().get("pluginInputJson");
            if (explicitJson instanceof String rawJson && !rawJson.isBlank()) {
                inputJson = rawJson;
            } else {
                Object input = atomicOp.parameters().getOrDefault("pluginInput", Map.of());
                inputJson = objectMapper.writeValueAsString(input);
            }
        } catch (Exception ignored) {
            // keep default json
        }

        String command = "PLUGIN_INPUT='" + escapeSingleQuote(inputJson) + "' " + manifest.entryCommand();
        CommandExecutionResult result = localCommandExecutor.execute(List.of("sh", "-lc", command), atomicOp.timeoutSeconds());
        ContextFrame frame = new ContextFrame(
                "plugin",
                atomicOp.type(),
                providerName(),
                manifest.pluginId(),
                Map.of(
                        "version", nullSafe(manifest.version()),
                        "isolatedProcess", String.valueOf(manifest.isolatedProcess())
                ),
                Instant.now()
        );
        if (!result.success()) {
            return new OpExecutionResult(
                    false,
                    providerName(),
                    "Plugin command failed: " + summarize(result.stderr(), result.error()),
                    List.of(frame),
                    "PLUGIN_EXECUTION_FAILED",
                    "Check plugin entryCommand and runtime dependencies"
            );
        }
        return new OpExecutionResult(
                true,
                providerName(),
                "Plugin executed: " + summarize(result.stdout(), "ok"),
                List.of(frame),
                null,
                null
        );
    }

    private String value(AtomicOp op, String key) {
        Object raw = op.parameters().get(key);
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw).trim();
        return value.isBlank() ? null : value;
    }

    private String summarize(String message, String fallback) {
        String content = (message == null || message.isBlank()) ? fallback : message;
        if (content == null || content.isBlank()) {
            return "no output";
        }
        String compact = content.replace('\n', ' ').replace('\r', ' ').trim();
        return compact.length() > 160 ? compact.substring(0, 160) + "..." : compact;
    }

    private String escapeSingleQuote(String text) {
        return text.replace("'", "'\"'\"'");
    }

    private String nullSafe(String text) {
        return text == null ? "" : text;
    }
}
