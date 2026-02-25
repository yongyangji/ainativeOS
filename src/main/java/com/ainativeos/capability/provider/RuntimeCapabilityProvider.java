package com.ainativeos.capability.provider;

import com.ainativeos.capability.CapabilityProvider;
import com.ainativeos.domain.AtomicOp;
import com.ainativeos.domain.ContextFrame;
import com.ainativeos.domain.OpExecutionResult;
import com.ainativeos.runtime.CommandExecutionResult;
import com.ainativeos.runtime.LocalCommandExecutor;
import com.ainativeos.runtime.SshCommandExecutor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class RuntimeCapabilityProvider implements CapabilityProvider {

    private final LocalCommandExecutor localCommandExecutor;
    private final SshCommandExecutor sshCommandExecutor;

    public RuntimeCapabilityProvider(LocalCommandExecutor localCommandExecutor, SshCommandExecutor sshCommandExecutor) {
        this.localCommandExecutor = localCommandExecutor;
        this.sshCommandExecutor = sshCommandExecutor;
    }

    @Override
    public boolean supports(String opType) {
        return opType.startsWith("RUNTIME_");
    }

    @Override
    public String providerName() {
        return "runtime-provider";
    }

    @Override
    public OpExecutionResult execute(AtomicOp atomicOp) {
        boolean simulateFailure = Boolean.TRUE.equals(atomicOp.parameters().get("simulateFailure"));

        List<ContextFrame> frames = new ArrayList<>();
        frames.add(new ContextFrame(
                "runtime",
                atomicOp.type(),
                providerName(),
                "wasm-container-substrate",
                Map.of("op", atomicOp.opId()),
                Instant.now()
        ));

        if (simulateFailure) {
            return new OpExecutionResult(
                    false,
                    providerName(),
                    "Runtime apply failed: unresolved dependency",
                    frames,
                    "RUNTIME_DEPENDENCY_UNRESOLVED",
                    "Patch operation parameters and retry"
            );
        }

        Object commandRaw = atomicOp.parameters().get("command");
        if (commandRaw instanceof String cmd && !cmd.isBlank()) {
            CommandExecutionResult commandResult = executeCommand(atomicOp, cmd);
            frames.add(new ContextFrame(
                    "runtime-command",
                    atomicOp.type(),
                    providerName(),
                    targetFingerprint(atomicOp),
                    Map.of(
                            "exitCode", String.valueOf(commandResult.exitCode()),
                            "durationMs", String.valueOf(commandResult.durationMs())
                    ),
                    Instant.now()
            ));

            if (!commandResult.success()) {
                return new OpExecutionResult(
                        false,
                        providerName(),
                        "Runtime command failed: " + summarize(commandResult.stderr(), commandResult.error()),
                        frames,
                        "RUNTIME_COMMAND_FAILED",
                        "Validate runtimeCommand and dependencies"
                );
            }

            return new OpExecutionResult(
                    true,
                    providerName(),
                    "Runtime command executed successfully: " + summarize(commandResult.stdout(), null),
                    frames,
                    null,
                    null
            );
        }

        return new OpExecutionResult(true, providerName(), "Declarative runtime state applied", frames, null, null);
    }

    @Override
    public void rollback(AtomicOp atomicOp) {
        // no-op in current MVP; runtime rollback hook is reserved
    }

    private List<String> buildShellCommand(String cmd) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return List.of("powershell", "-Command", cmd);
        }
        return List.of("sh", "-lc", cmd);
    }

    private CommandExecutionResult executeCommand(AtomicOp atomicOp, String command) {
        String remoteHost = value(atomicOp, "remoteHost");
        String remoteUser = value(atomicOp, "remoteUser");
        String privateKeyBase64 = value(atomicOp, "remotePrivateKeyBase64");
        String privateKey = value(atomicOp, "remotePrivateKey");
        String passphrase = value(atomicOp, "remotePassphrase");
        int port = parseIntOrDefault(value(atomicOp, "remotePort"), 22);
        if (remoteHost != null && remoteUser != null && privateKeyBase64 != null) {
            return sshCommandExecutor.executeWithKeyBase64(
                    remoteHost,
                    port,
                    remoteUser,
                    privateKeyBase64,
                    passphrase,
                    command,
                    atomicOp.timeoutSeconds()
            );
        }
        if (remoteHost != null && remoteUser != null && privateKey != null) {
            return sshCommandExecutor.executeWithKey(
                    remoteHost,
                    port,
                    remoteUser,
                    privateKey,
                    passphrase,
                    command,
                    atomicOp.timeoutSeconds()
            );
        }
        String remotePassword = value(atomicOp, "remotePassword");
        if (remoteHost != null && remoteUser != null && remotePassword != null) {
            return sshCommandExecutor.execute(
                    remoteHost,
                    port,
                    remoteUser,
                    remotePassword,
                    command,
                    atomicOp.timeoutSeconds()
            );
        }
        List<String> shellCommand = buildShellCommand(command);
        return localCommandExecutor.execute(shellCommand, atomicOp.timeoutSeconds());
    }

    private String targetFingerprint(AtomicOp atomicOp) {
        String remoteHost = value(atomicOp, "remoteHost");
        if (remoteHost != null) {
            return "ssh://" + remoteHost;
        }
        return "local-process";
    }

    private String value(AtomicOp op, String key) {
        Object val = op.parameters().get(key);
        if (val instanceof String s && !s.isBlank()) {
            return s;
        }
        return null;
    }

    private int parseIntOrDefault(String val, int defaultValue) {
        if (val == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
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
