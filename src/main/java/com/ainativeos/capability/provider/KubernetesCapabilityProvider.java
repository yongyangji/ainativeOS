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
 * Kubernetes 能力 Provider。
 */
@Component
public class KubernetesCapabilityProvider implements CapabilityProvider {

    private final LocalCommandExecutor localCommandExecutor;

    public KubernetesCapabilityProvider(LocalCommandExecutor localCommandExecutor) {
        this.localCommandExecutor = localCommandExecutor;
    }

    @Override
    public boolean supports(String opType) {
        return opType.startsWith("K8S_");
    }

    @Override
    public String providerName() {
        return "k8s-provider";
    }

    @Override
    public OpExecutionResult execute(AtomicOp atomicOp) {
        String command = resolveCommand(atomicOp);
        CommandExecutionResult result = localCommandExecutor.execute(List.of("sh", "-lc", command), atomicOp.timeoutSeconds());
        ContextFrame frame = new ContextFrame(
                "kubernetes",
                atomicOp.type(),
                providerName(),
                "kubectl-cli",
                Map.of("command", command),
                Instant.now()
        );
        if (!result.success()) {
            return new OpExecutionResult(
                    false,
                    providerName(),
                    "K8s command failed: " + summarize(result.stderr(), result.error()),
                    List.of(frame),
                    "K8S_COMMAND_FAILED",
                    "Ensure kubectl and kubeconfig are available"
            );
        }
        return new OpExecutionResult(true, providerName(), "K8s command success", List.of(frame), null, null);
    }

    private String resolveCommand(AtomicOp op) {
        if ("K8S_APPLY_MANIFEST".equals(op.type())) {
            String path = value(op, "manifestPath");
            if (path == null) {
                return "echo missing manifestPath";
            }
            return "kubectl apply -f " + path;
        }
        return value(op, "command") != null ? value(op, "command") : "kubectl get pods -A";
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

