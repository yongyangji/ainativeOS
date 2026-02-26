package com.ainativeos.kernel.healing;

import com.ainativeos.domain.AtomicOp;
import com.ainativeos.domain.ErrorVector;
import com.ainativeos.domain.FailureObject;
import com.ainativeos.domain.Recoverability;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepairPlannerTest {

    private final RepairPlanner repairPlanner = new RepairPlanner();

    @Test
    void patchForRetry_shouldRemoveSimulateFailureAndIncreaseTimeoutOnTimeoutSignal() {
        AtomicOp failedOp = new AtomicOp(
                "op-apply",
                "RUNTIME_APPLY_DECLARATIVE_STATE",
                "apply",
                Map.of("simulateFailure", true, "runtimeCommand", "echo hello"),
                true,
                true,
                60
        );
        FailureObject failure = new FailureObject(
                "failure-1",
                "goal-1",
                "op-apply",
                List.of(),
                List.of(new ErrorVector("execution", "RUNTIME_TIMEOUT", "runtime", Recoverability.RETRYABLE, 0.9, "increase timeout")),
                List.of(),
                "retry-1",
                Map.of()
        );

        AtomicOp patched = repairPlanner.patchForRetry(failedOp, failure);

        assertFalse(Boolean.parseBoolean(String.valueOf(patched.parameters().getOrDefault("simulateFailure", false))));
        assertEquals("self-healing-vfs", patched.parameters().get("patchedBy"));
        assertEquals("retry-1", patched.parameters().get("retryToken"));
        assertTrue(patched.timeoutSeconds() > failedOp.timeoutSeconds());
        assertTrue(patched.description().contains("[patched]"));
    }

    @Test
    void patchForRetry_shouldKeepTimeoutWhenNoTimeoutSignal() {
        AtomicOp failedOp = new AtomicOp("op-1", "COMPUTE_VERIFY_SUCCESS", "verify", Map.of(), true, false, 50);
        FailureObject failure = new FailureObject(
                "failure-2",
                "goal-2",
                "op-1",
                List.of(),
                List.of(new ErrorVector("execution", "NOT_FOUND", "verify", Recoverability.REPAIRABLE, 0.9, "check id")),
                List.of(),
                "retry-2",
                Map.of()
        );

        AtomicOp patched = repairPlanner.patchForRetry(failedOp, failure);

        assertEquals(50, patched.timeoutSeconds());
    }
}

