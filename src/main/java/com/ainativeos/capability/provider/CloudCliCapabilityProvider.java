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
 * 云 CLI 能力 Provider（AWS/Azure/GCP 统一入口）。
 */
@Component
public class CloudCliCapabilityProvider implements CapabilityProvider {

    private final LocalCommandExecutor localCommandExecutor;

    public CloudCliCapabilityProvider(LocalCommandExecutor localCommandExecutor) {
        this.localCommandExecutor = localCommandExecutor;
    }

    @Override
    public boolean supports(String opType) {
        return opType.startsWith("CLOUD_");
    }

    @Override
    public String providerName() {
        return "cloud-provider";
    }

    @Override
    public OpExecutionResult execute(AtomicOp atomicOp) {
        String command = value(atomicOp, "cloudCommand");
        if (command == null) {
            command = value(atomicOp, "command");
        }
        if (command == null) {
            command = "echo missing cloudCommand";
        }
        CommandExecutionResult result = localCommandExecutor.execute(List.of("sh", "-lc", command), atomicOp.timeoutSeconds());
        ContextFrame frame = new ContextFrame(
                "cloud",
                atomicOp.type(),
                providerName(),
                value(atomicOp, "cloudProvider") == null ? "cloud-cli-generic" : value(atomicOp, "cloudProvider"),
                Map.of("command", command),
                Instant.now()
        );
        if (!result.success()) {
            return new OpExecutionResult(
                    false,
                    providerName(),
                    "Cloud command failed: " + summarize(result.stderr(), result.error()),
                    List.of(frame),
                    "CLOUD_COMMAND_FAILED",
                    "Ensure cloud CLI auth and command syntax"
            );
        }
        return new OpExecutionResult(true, providerName(), "Cloud command success", List.of(frame), null, null);
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

