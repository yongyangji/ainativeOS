package com.ainativeos.capability.provider;

import com.ainativeos.capability.CapabilityProvider;
import com.ainativeos.domain.AtomicOp;
import com.ainativeos.domain.ContextFrame;
import com.ainativeos.domain.OpExecutionResult;
import com.ainativeos.runtime.CommandExecutionResult;
import com.ainativeos.runtime.DesiredStateReconciler;
import com.ainativeos.runtime.ReconcileResult;
import com.ainativeos.runtime.RuntimeCommandDispatcher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
/**
 * 运行时能力 Provider。
 * <p>
 * 支持三种执行路径：
 * 1. 本地 shell 执行（LocalCommandExecutor）
 * 2. SSH 密码认证执行
 * 3. SSH 私钥认证执行（Base64 或原始 PEM）
 */
public class RuntimeCapabilityProvider implements CapabilityProvider {

    private final RuntimeCommandDispatcher runtimeCommandDispatcher;
    private final DesiredStateReconciler desiredStateReconciler;

    public RuntimeCapabilityProvider(
            RuntimeCommandDispatcher runtimeCommandDispatcher,
            DesiredStateReconciler desiredStateReconciler
    ) {
        this.runtimeCommandDispatcher = runtimeCommandDispatcher;
        this.desiredStateReconciler = desiredStateReconciler;
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
        // 测试开关：用于主动制造首轮失败，验证自愈重试能力
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
            ReconcileResult reconcileResult = tryReconcile(atomicOp);
            if (reconcileResult != null) {
                frames.add(new ContextFrame(
                        "runtime-reconcile",
                        atomicOp.type(),
                        providerName(),
                        targetFingerprint(atomicOp),
                        Map.of(
                                "rounds", String.valueOf(reconcileResult.rounds()),
                                "reconcile", reconcileResult.success() ? "success" : "failed"
                        ),
                        Instant.now()
                ));
                if (!reconcileResult.success()) {
                    return new OpExecutionResult(
                            false,
                            providerName(),
                            "Runtime reconcile failed: " + reconcileResult.message(),
                            frames,
                            "RUNTIME_RECONCILE_FAILED",
                            "Check reconcileApplyCommand/reconcileVerifyCommand"
                    );
                }
                return new OpExecutionResult(
                        true,
                        providerName(),
                        "Runtime reconcile success in rounds=" + reconcileResult.rounds(),
                        frames,
                        null,
                        null
                );
            }

            // 根据参数自动选择本地/SSH执行路径
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
                // 统一失败模型，供执行引擎构建 FailureObject
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

    private ReconcileResult tryReconcile(AtomicOp atomicOp) {
        String applyCommand = value(atomicOp, "reconcileApplyCommand");
        String verifyCommand = value(atomicOp, "reconcileVerifyCommand");
        if (applyCommand == null || verifyCommand == null) {
            return null;
        }
        int rounds = parseIntOrDefault(value(atomicOp, "reconcileMaxRounds"), 5);
        long intervalMs = parseLongOrDefault(value(atomicOp, "reconcileIntervalMs"), 2000L);
        return desiredStateReconciler.reconcile(
                applyCommand,
                verifyCommand,
                rounds,
                intervalMs,
                cmd -> executeCommand(atomicOp, cmd)
        );
    }

    @Override
    public void rollback(AtomicOp atomicOp) {
        // no-op in current MVP; runtime rollback hook is reserved
    }

    private CommandExecutionResult executeCommand(AtomicOp atomicOp, String command) {
        return runtimeCommandDispatcher.execute(atomicOp.parameters(), command, atomicOp.timeoutSeconds());
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

    private long parseLongOrDefault(String val, long defaultValue) {
        if (val == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(val);
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
