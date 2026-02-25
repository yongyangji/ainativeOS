package com.ainativeos.capability.provider;

import com.ainativeos.capability.CapabilityProvider;
import com.ainativeos.domain.AtomicOp;
import com.ainativeos.domain.ContextFrame;
import com.ainativeos.domain.OpExecutionResult;
import com.ainativeos.runtime.CommandExecutionResult;
import com.ainativeos.runtime.LocalCommandExecutor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 系统能力 Provider。
 */
@Component
public class SystemCapabilityProvider implements CapabilityProvider {

    private final LocalCommandExecutor localCommandExecutor;

    public SystemCapabilityProvider(LocalCommandExecutor localCommandExecutor) {
        this.localCommandExecutor = localCommandExecutor;
    }

    @Override
    public boolean supports(String opType) {
        return opType.startsWith("SYSTEM_");
    }

    @Override
    public String providerName() {
        return "system-provider";
    }

    @Override
    public OpExecutionResult execute(AtomicOp atomicOp) {
        String command = resolveCommand(atomicOp);
        CommandExecutionResult result = localCommandExecutor.execute(buildShellCommand(command), atomicOp.timeoutSeconds());
        ContextFrame frame = new ContextFrame(
                "system",
                atomicOp.type(),
                providerName(),
                "local-system-adapter",
                Map.of("command", command),
                Instant.now()
        );

        if (!result.success()) {
            return new OpExecutionResult(
                    false,
                    providerName(),
                    "System command failed: " + summarize(result.stderr(), result.error()),
                    List.of(frame),
                    "SYSTEM_COMMAND_FAILED",
                    "Check package manager/service command availability"
            );
        }
        return new OpExecutionResult(true, providerName(), "System command success", List.of(frame), null, null);
    }

    private String resolveCommand(AtomicOp op) {
        if ("SYSTEM_PACKAGE_INSTALL".equals(op.type())) {
            String explicit = value(op, "packageInstallCommand");
            if (explicit != null && !explicit.isBlank()) {
                return explicit;
            }
            String pkg = value(op, "packageName");
            if (pkg == null || pkg.isBlank()) {
                return "echo missing packageName";
            }
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                return "winget install --id " + pkg + " -e";
            }
            return "sudo apt-get update && sudo apt-get install -y " + pkg;
        }
        if ("SYSTEM_SERVICE_STATUS".equals(op.type())) {
            return value(op, "serviceStatusCommand") != null ? value(op, "serviceStatusCommand") : "systemctl status";
        }
        return value(op, "command") != null ? value(op, "command") : "echo unsupported system op";
    }

    private List<String> buildShellCommand(String cmd) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return List.of("powershell", "-Command", cmd);
        }
        return List.of("sh", "-lc", cmd);
    }

    private String value(AtomicOp op, String key) {
        Object val = op.parameters().get(key);
        if (val instanceof String s && !s.isBlank()) {
            return s;
        }
        return null;
    }

    private String summarize(String message, String fallback) {
        String content = (message == null || message.isBlank()) ? fallback : message;
        if (content == null || content.isBlank()) {
            return "no output";
        }
        String compact = content.replace('\n', ' ').replace('\r', ' ').trim();
        return compact.length() > 160 ? compact.substring(0, 160) + "..." : compact;
    }
}

