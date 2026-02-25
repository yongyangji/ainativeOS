package com.ainativeos.kernel.healing;

import com.ainativeos.domain.AtomicOp;
import com.ainativeos.domain.FailureObject;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class RepairPlanner {

    public AtomicOp patchForRetry(AtomicOp failedOp, FailureObject failureObject) {
        Map<String, Object> patched = new HashMap<>(failedOp.parameters());
        patched.remove("simulateFailure");
        patched.put("patchedBy", "self-healing-vfs");
        patched.put("retryToken", failureObject.retryToken());
        return new AtomicOp(
                failedOp.opId(),
                failedOp.type(),
                failedOp.description() + " [patched]",
                patched,
                failedOp.idempotent(),
                failedOp.rollbackSupported(),
                failedOp.timeoutSeconds()
        );
    }
}
