package com.ainativeos.kernel.policy;

import com.ainativeos.domain.GoalSpec;
import com.ainativeos.runtime.CommandExecutionResult;
import com.ainativeos.runtime.LocalCommandExecutor;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
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

        boolean remoteMode = constraints.containsKey("remoteHost") && constraints.containsKey("remoteUser");
        details.put("remoteMode", remoteMode);
        if (remoteMode) {
            details.put("remoteValidation", "skipped");
            return ValidationResult.valid(details);
        }

        if ("true".equalsIgnoreCase(constraints.getOrDefault("requiresDocker", "false"))
                && !hasCommand("docker")) {
            return ValidationResult.invalid("Environment constraint failed: docker is required", details);
        }

        if ("true".equalsIgnoreCase(constraints.getOrDefault("requiresKubectl", "false"))
                && !hasCommand("kubectl")) {
            return ValidationResult.invalid("Environment constraint failed: kubectl is required", details);
        }

        if ("gpu".equals(computeClass) && !hasCommand("nvidia-smi")) {
            return ValidationResult.invalid("Environment constraint failed: computeClass=gpu requires nvidia-smi", details);
        }

        if ("highmem".equals(computeClass)) {
            int minMemoryGb = parseIntOrDefault(constraints.get("minMemoryGb"), 16);
            long availableGb = availableMemoryGb();
            details.put("availableMemoryGb", availableGb);
            details.put("requiredMemoryGb", minMemoryGb);
            if (availableGb > 0 && availableGb < minMemoryGb) {
                return ValidationResult.invalid("Environment constraint failed: insufficient memory for highmem", details);
            }
        }

        String requiredCommands = constraints.get("requiredCommands");
        if (requiredCommands != null && !requiredCommands.isBlank()) {
            List<String> commands = List.of(requiredCommands.split(","));
            for (String command : commands) {
                String trimmed = command.trim();
                if (!trimmed.isBlank() && !hasCommand(trimmed)) {
                    return ValidationResult.invalid("Environment constraint failed: missing command " + trimmed, details);
                }
            }
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

    private int parseIntOrDefault(String raw, int fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
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
