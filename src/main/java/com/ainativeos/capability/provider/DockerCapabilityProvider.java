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

@Component
public class DockerCapabilityProvider implements CapabilityProvider {

    private final LocalCommandExecutor localCommandExecutor;

    public DockerCapabilityProvider(LocalCommandExecutor localCommandExecutor) {
        this.localCommandExecutor = localCommandExecutor;
    }

    @Override
    public boolean supports(String opType) {
        return opType.startsWith("DOCKER_");
    }

    @Override
    public String providerName() {
        return "docker-provider";
    }

    @Override
    public List<String> advertisedOpTypes() {
        return List.of("DOCKER_RUN_IMAGE", "DOCKER_VERIFY_CONTAINER", "DOCKER_ROLLBACK_CONTAINER", "DOCKER_EXECUTE");
    }

    @Override
    public OpExecutionResult execute(AtomicOp atomicOp) {
        String command = resolveCommand(atomicOp);
        CommandExecutionResult result = localCommandExecutor.execute(List.of("sh", "-lc", command), atomicOp.timeoutSeconds());
        ContextFrame frame = new ContextFrame(
                "docker",
                atomicOp.type(),
                providerName(),
                "docker-cli",
                Map.of("command", command),
                Instant.now()
        );
        if (!result.success()) {
            return new OpExecutionResult(
                    false,
                    providerName(),
                    "Docker command failed: " + summarize(result.stderr(), result.error()),
                    List.of(frame),
                    "DOCKER_COMMAND_FAILED",
                    "Ensure docker daemon is running and command is valid"
            );
        }
        return new OpExecutionResult(true, providerName(), "Docker command success", List.of(frame), null, null);
    }

    @Override
    public void rollback(AtomicOp atomicOp) {
        String containerName = value(atomicOp, "containerName");
        if (containerName == null) {
            return;
        }
        String cmd = "docker rm -f " + containerName + " >/dev/null 2>&1 || true";
        localCommandExecutor.execute(List.of("sh", "-lc", cmd), Math.max(30, atomicOp.timeoutSeconds()));
    }

    private String resolveCommand(AtomicOp op) {
        if ("DOCKER_RUN_IMAGE".equals(op.type())) {
            String image = value(op, "image");
            if (image == null) {
                return "echo missing image";
            }
            String containerName = value(op, "containerName");
            String runArgs = value(op, "runArgs");
            StringBuilder sb = new StringBuilder("docker run -d");
            if (containerName != null) {
                sb.append(" --name ").append(containerName);
            }
            if (runArgs != null) {
                sb.append(" ").append(runArgs);
            }
            sb.append(" ").append(image);
            return sb.toString();
        }
        if ("DOCKER_VERIFY_CONTAINER".equals(op.type())) {
            String containerName = value(op, "containerName");
            if (containerName == null) {
                return "echo missing containerName";
            }
            return "docker inspect -f '{{.State.Running}}' " + containerName + " | grep true";
        }
        if ("DOCKER_ROLLBACK_CONTAINER".equals(op.type())) {
            String containerName = value(op, "containerName");
            if (containerName == null) {
                return "echo missing containerName";
            }
            return "docker rm -f " + containerName;
        }
        String command = value(op, "command");
        return command != null ? command : "docker ps";
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
