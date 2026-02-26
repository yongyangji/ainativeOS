package com.ainativeos.plugin;

import java.util.List;
import java.util.Map;

public record PluginManifest(
        String pluginId,
        String name,
        String version,
        String description,
        String entryCommand,
        boolean enabled,
        boolean isolatedProcess,
        List<String> requiredCapabilities,
        Map<String, Object> inputSchema,
        Map<String, Object> outputSchema,
        Map<String, Object> metadata
) {
}
