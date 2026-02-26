package com.ainativeos.kernel.healing;

import com.ainativeos.domain.AtomicOp;
import com.ainativeos.domain.ContextFrame;
import com.ainativeos.domain.FailureObject;
import com.ainativeos.domain.OpExecutionResult;
import com.ainativeos.domain.Recoverability;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FailureAnalyzerTest {

    private final FailureAnalyzer failureAnalyzer = new FailureAnalyzer();

    @Test
    void buildFailure_shouldClassifyAuthErrorAsNonRecoverable() {
        AtomicOp op = new AtomicOp("op-1", "SYSTEM_PACKAGE_INSTALL", "install", Map.of(), true, true, 60);
        ContextFrame frame = new ContextFrame("execution", "SYSTEM_PACKAGE_INSTALL", "system-provider", "host", Map.of(), Instant.now());
        OpExecutionResult result = new OpExecutionResult(
                false,
                "system-provider",
                "permission denied",
                List.of(frame),
                "AUTH_FORBIDDEN",
                "check credential"
        );

        FailureObject failure = failureAnalyzer.buildFailure("goal-1", op, result, 1);

        assertEquals("goal-1", failure.goalId());
        assertEquals("op-1", failure.failedOpId());
        assertEquals(Recoverability.NON_RECOVERABLE, failure.errorVectors().get(0).recoverability());
        assertTrue(failure.patchHints().stream().anyMatch(it -> it.contains("credentials")));
    }

    @Test
    void mergeContext_shouldConcatenateExistingAndIncoming() {
        ContextFrame existing = new ContextFrame("a", "A", "p1", "f1", Map.of(), Instant.now());
        ContextFrame incoming = new ContextFrame("b", "B", "p2", "f2", Map.of(), Instant.now());

        List<ContextFrame> merged = failureAnalyzer.mergeContext(List.of(existing), List.of(incoming));

        assertEquals(2, merged.size());
    }
}

