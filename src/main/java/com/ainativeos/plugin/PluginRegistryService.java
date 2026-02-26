package com.ainativeos.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class PluginRegistryService {

    private static final Logger log = LoggerFactory.getLogger(PluginRegistryService.class);
    private final PluginProperties pluginProperties;
    private final ObjectMapper objectMapper;

    public PluginRegistryService(PluginProperties pluginProperties, ObjectMapper objectMapper) {
        this.pluginProperties = pluginProperties;
        this.objectMapper = objectMapper;
    }

    public List<PluginManifest> list() {
        if (!pluginProperties.isEnabled()) {
            return List.of();
        }

        Path dir = Path.of(pluginProperties.getManifestDir());
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return List.of();
        }

        List<PluginManifest> manifests = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(path -> readManifest(path).ifPresent(manifests::add));
        } catch (IOException e) {
            log.warn("Failed to scan plugin directory {}: {}", dir, e.getMessage());
        }
        return manifests;
    }

    public Optional<PluginManifest> findById(String pluginId) {
        return list().stream()
                .filter(it -> it.pluginId() != null && it.pluginId().equals(pluginId))
                .findFirst();
    }

    private Optional<PluginManifest> readManifest(Path path) {
        try {
            PluginManifest manifest = objectMapper.readValue(path.toFile(), PluginManifest.class);
            if (manifest.pluginId() == null || manifest.pluginId().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(manifest);
        } catch (Exception e) {
            log.warn("Invalid plugin manifest {}: {}", path, e.getMessage());
            return Optional.empty();
        }
    }
}
