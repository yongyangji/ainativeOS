package com.ainativeos.capability.provider;

import com.ainativeos.capability.CapabilityProvider;
import com.ainativeos.domain.AtomicOp;
import com.ainativeos.domain.ContextFrame;
import com.ainativeos.domain.OpExecutionResult;
import com.ainativeos.runtime.CommandExecutionResult;
import com.ainativeos.runtime.LocalCommandExecutor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class RuntimeCapabilityProvider implements CapabilityProvider {

    private final LocalCommandExecutor localCommandExecutor;

    public RuntimeCapabilityProvider(LocalCommandExecutor localCommandExecutor) {
        this.localCommandExecutor = localCommandExecutor;
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
            List<String> shellCommand = buildShellCommand(cmd);
            CommandExecutionResult commandResult = localCommandExecutor.execute(shellCommand, atomicOp.timeoutSeconds());
            frames.add(new ContextFrame(
                    "runtime-command",
                    atomicOp.type(),
                    providerName(),
                    "local-process",
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

    private String summarize(String message, String fallback) {
        String content = (message == null || message.isBlank()) ? fallback : message;
        if (content == null || content.isBlank()) {
            return "no output";
        }
        String compact = content.replace('\n', ' ').replace('\r', ' ').trim();
        return compact.length() > 160 ? compact.substring(0, 160) + "..." : compact;
    }
}
