package com.ainativeos.kernel.healing;

import com.ainativeos.domain.AtomicOp;
import com.ainativeos.domain.ContextFrame;
import com.ainativeos.domain.ErrorVector;
import com.ainativeos.domain.FailureObject;
import com.ainativeos.domain.OpExecutionResult;
import com.ainativeos.domain.Recoverability;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class FailureAnalyzer {

    public FailureObject buildFailure(String goalId, AtomicOp op, OpExecutionResult opResult, int attempt) {
        ErrorVector vector = new ErrorVector(
                "execution",
                opResult.errorCode() == null ? "UNKNOWN_ERROR" : opResult.errorCode(),
                op.type(),
                inferRecoverability(opResult.errorCode()),
                0.92,
                opResult.recommendation() == null ? "Inspect provider diagnostics" : opResult.recommendation()
        );

        List<String> patchHints = List.of(
                "remove simulateFailure flag when dependency is available",
                "switch provider or fallback image",
                "revalidate desired state constraints"
        );

        return new FailureObject(
                UUID.randomUUID().toString(),
                goalId,
                op.opId(),
                opResult.contextFrames(),
                List.of(vector),
                patchHints,
                UUID.randomUUID().toString(),
                Map.of(
                        "attempt", attempt,
                        "provider", opResult.provider(),
                        "message", opResult.message()
                )
        );
    }

    public List<ContextFrame> mergeContext(List<ContextFrame> existing, List<ContextFrame> incoming) {
        if (existing == null || existing.isEmpty()) {
            return incoming;
        }
        if (incoming == null || incoming.isEmpty()) {
            return existing;
        }
        return java.util.stream.Stream.concat(existing.stream(), incoming.stream()).toList();
    }

    private Recoverability inferRecoverability(String errorCode) {
        if (errorCode == null) {
            return Recoverability.REPAIRABLE;
        }
        if (errorCode.contains("NOT_FOUND") || errorCode.contains("UNRESOLVED")) {
            return Recoverability.REPAIRABLE;
        }
        return Recoverability.RETRYABLE;
    }
}
