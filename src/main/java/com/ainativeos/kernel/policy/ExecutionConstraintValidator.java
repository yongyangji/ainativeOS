package com.ainativeos.kernel.policy;

import com.ainativeos.domain.GoalSpec;
import com.ainativeos.runtime.CommandExecutionResult;
import com.ainativeos.runtime.LocalCommandExecutor;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ExecutionConstraintValidator {

    private static final Set<String> SUPPORTED_COMPUTE_CLASS = Set.of("cpu", "gpu", "highmem");
    private final LocalCommandExecutor localCommandExecutor;

    public ExecutionConstraintValidator(LocalCommandExecutor localCommandExecutor) {
        this.localCommandExecutor = localCommandExecutor;
    }

    public ValidationResult validate(GoalSpec goalSpec) {
        Map<String, String> constraints = goalSpec.constraints() == null ? Map.of() : goalSpec.constraints();
        Map<String, Object> details = new HashMap<>();

        String computeClass = constraints.getOrDefault("computeClass", "cpu").toLowerCase();
        details.put("computeClass", computeClass);
        if (!SUPPORTED_COMPUTE_CLASS.contains(computeClass)) {
            return ValidationResult.invalid("Unsupported computeClass: " + computeClass, details);
        }

        ParseResult requiresDocker = parseBoolean(constraints.get("requiresDocker"), false);
        if (!requiresDocker.valid()) {
            return ValidationResult.invalid("Invalid boolean value for requiresDocker", details);
        }
        ParseResult requiresKubectl = parseBoolean(constraints.get("requiresKubectl"), false);
        if (!requiresKubectl.valid()) {
            return ValidationResult.invalid("Invalid boolean value for requiresKubectl", details);
        }

        boolean remoteMode = hasNonBlank(constraints, "remoteHost") && hasNonBlank(constraints, "remoteUser");
        details.put("remoteMode", remoteMode);
        if (remoteMode) {
            ValidationResult remoteValidation = validateRemoteConstraints(constraints, details);
            if (!remoteValidation.valid()) {
                return remoteValidation;
            }
            details.put("remoteValidation", "performed");
            return ValidationResult.valid(details);
        }

        if (requiresDocker.value() && !hasCommand("docker")) {
            return ValidationResult.invalid("Environment constraint failed: docker is required", details);
        }

        if (requiresKubectl.value() && !hasCommand("kubectl")) {
            return ValidationResult.invalid("Environment constraint failed: kubectl is required", details);
        }

        if ("gpu".equals(computeClass) && !hasCommand("nvidia-smi")) {
            return ValidationResult.invalid("Environment constraint failed: computeClass=gpu requires nvidia-smi", details);
        }

        if ("highmem".equals(computeClass)) {
            ParseIntResult minMemoryGbParse = parsePositiveInt(constraints.get("minMemoryGb"), 16);
            if (!minMemoryGbParse.valid()) {
                return ValidationResult.invalid("Invalid minMemoryGb: must be a positive integer", details);
            }
            int minMemoryGb = minMemoryGbParse.value();
            long availableGb = availableMemoryGb();
            details.put("availableMemoryGb", availableGb);
            details.put("requiredMemoryGb", minMemoryGb);
            if (availableGb > 0 && availableGb < minMemoryGb) {
                return ValidationResult.invalid("Environment constraint failed: insufficient memory for highmem", details);
            }
        }

        ValidationResult requiredCommandsValidation = validateRequiredCommands(constraints, details);
        if (!requiredCommandsValidation.valid()) {
            return requiredCommandsValidation;
        }

        return ValidationResult.valid(details);
    }

    private ValidationResult validateRemoteConstraints(Map<String, String> constraints, Map<String, Object> details) {
        String authType = null;
        if (hasNonBlank(constraints, "remotePrivateKeyBase64")) {
            authType = "privateKeyBase64";
        } else if (hasNonBlank(constraints, "remotePrivateKey")) {
            authType = "privateKey";
        } else if (hasNonBlank(constraints, "remotePassword")) {
            authType = "password";
        }
        details.put("remoteAuthType", authType == null ? "none" : authType);
        if (authType == null) {
            return ValidationResult.invalid(
                    "Environment constraint failed: remote mode requires remotePassword or remotePrivateKeyBase64 or remotePrivateKey",
                    details
            );
        }

        if (hasNonBlank(constraints, "remotePort")) {
            ParseIntResult remotePortParse = parsePositiveInt(constraints.get("remotePort"), 22);
            if (!remotePortParse.valid() || remotePortParse.value() > 65535) {
                return ValidationResult.invalid("Invalid remotePort: must be 1-65535", details);
            }
            details.put("remotePort", remotePortParse.value());
        }
        return ValidationResult.valid(details);
    }

    private ValidationResult validateRequiredCommands(Map<String, String> constraints, Map<String, Object> details) {
        String requiredCommands = constraints.get("requiredCommands");
        if (requiredCommands == null || requiredCommands.isBlank()) {
            details.put("requiredCommands", List.of());
            return ValidationResult.valid(details);
        }
        List<String> commands = List.of(requiredCommands.split(","));
        List<String> normalized = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String command : commands) {
            String trimmed = command.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            normalized.add(trimmed);
            if (!hasCommand(trimmed)) {
                missing.add(trimmed);
            }
        }
        details.put("requiredCommands", normalized);
        details.put("missingCommands", missing);
        if (!missing.isEmpty()) {
            return ValidationResult.invalid("Environment constraint failed: missing command(s) " + String.join(",", missing), details);
        }
        return ValidationResult.valid(details);
    }

    private boolean hasCommand(String command) {
        String os = System.getProperty("os.name", "").toLowerCase();
        List<String> shell = os.contains("win")
                ? List.of("powershell", "-Command", "Get-Command " + command + " -ErrorAction SilentlyContinue")
                : List.of("sh", "-lc", "command -v " + command);
        CommandExecutionResult result = localCommandExecutor.execute(shell, 8);
        return result.success();
    }

    private long availableMemoryGb() {
        try {
            java.lang.management.OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            if (osBean instanceof com.sun.management.OperatingSystemMXBean sunBean) {
                return sunBean.getTotalMemorySize() / (1024L * 1024L * 1024L);
            }
        } catch (Exception ignored) {
            // ignore
        }
        return -1;
    }

    private ParseIntResult parsePositiveInt(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return new ParseIntResult(true, fallback);
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return new ParseIntResult(value > 0, value);
        } catch (NumberFormatException ignored) {
            return new ParseIntResult(false, fallback);
        }
    }

    private ParseResult parseBoolean(String raw, boolean fallback) {
        if (raw == null || raw.isBlank()) {
            return new ParseResult(true, fallback);
        }
        String normalized = raw.trim().toLowerCase();
        if ("true".equals(normalized)) {
            return new ParseResult(true, true);
        }
        if ("false".equals(normalized)) {
            return new ParseResult(true, false);
        }
        return new ParseResult(false, fallback);
    }

    private boolean hasNonBlank(Map<String, String> constraints, String key) {
        String value = constraints.get(key);
        return value != null && !value.isBlank();
    }

    private record ParseResult(boolean valid, boolean value) {
    }

    private record ParseIntResult(boolean valid, int value) {
    }

    public record ValidationResult(
            boolean valid,
            String reason,
            Map<String, Object> details
    ) {
        static ValidationResult valid(Map<String, Object> details) {
            return new ValidationResult(true, "ok", details);
        }

        static ValidationResult invalid(String reason, Map<String, Object> details) {
            return new ValidationResult(false, reason, details);
        }
    }
}

