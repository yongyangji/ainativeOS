package com.ainativeos.kernel.healing;

import com.ainativeos.domain.AtomicOp;
import com.ainativeos.domain.FailureObject;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
/**
 * 修复规划器。
 * <p>
 * 根据 FailureObject 生成可重试的补丁版 AtomicOp。
 */
public class RepairPlanner {

    public AtomicOp patchForRetry(AtomicOp failedOp, FailureObject failureObject) {
        Map<String, Object> patched = new HashMap<>(failedOp.parameters());
        patched.remove("simulateFailure");
        patched.put("patchedBy", "self-healing-vfs");
        patched.put("retryToken", failureObject.retryToken());
        patched.put("patchedAt", Instant.now().toString());
        patched.put("failureId", failureObject.failureId());

        int timeout = failedOp.timeoutSeconds();
        if (hasTimeoutSignal(failureObject) && timeout < 300) {
            timeout = Math.min(300, timeout + 30);
        }

        return new AtomicOp(
                failedOp.opId(),
                failedOp.type(),
                failedOp.description() + " [patched]",
                patched,
                failedOp.idempotent(),
                failedOp.rollbackSupported(),
                timeout
        );
    }

    private boolean hasTimeoutSignal(FailureObject failureObject) {
        return failureObject.errorVectors() != null
                && failureObject.errorVectors().stream()
                .anyMatch(v -> v.code() != null && v.code().toUpperCase().contains("TIMEOUT"));
    }
}
