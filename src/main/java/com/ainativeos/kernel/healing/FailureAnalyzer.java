package com.ainativeos.kernel.healing;

import com.ainativeos.domain.AtomicOp;
import com.ainativeos.domain.ContextFrame;
import com.ainativeos.domain.ErrorVector;
import com.ainativeos.domain.FailureObject;
import com.ainativeos.domain.OpExecutionResult;
import com.ainativeos.domain.Recoverability;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
/**
 * 失败分析器。
 * <p>
 * 将 Provider 返回的失败结果转为统一 FailureObject，
 * 供自修复策略、审计与后续排障使用。
 */
public class FailureAnalyzer {

    public FailureObject buildFailure(String goalId, AtomicOp op, OpExecutionResult opResult, int attempt) {
        ErrorVector vector = new ErrorVector(
                "execution",
                opResult.errorCode() == null ? "UNKNOWN_ERROR" : opResult.errorCode(),
                op.type(),
                inferRecoverability(opResult.errorCode()),
                confidenceScore(opResult.errorCode()),
                opResult.recommendation() == null ? "Inspect provider diagnostics" : opResult.recommendation()
        );

        List<String> patchHints = suggestPatchHints(op, opResult);

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
                        "message", opResult.message(),
                        "errorCode", opResult.errorCode() == null ? "UNKNOWN_ERROR" : opResult.errorCode()
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

    private List<String> suggestPatchHints(AtomicOp op, OpExecutionResult opResult) {
        List<String> hints = new ArrayList<>();
        hints.add("remove simulateFailure flag when dependency is available");

        String code = opResult.errorCode() == null ? "" : opResult.errorCode().toUpperCase();
        if (code.contains("TIMEOUT")) {
            hints.add("increase op timeout or reduce payload scope");
        }
        if (code.contains("AUTH") || code.contains("PERMISSION") || code.contains("FORBIDDEN")) {
            hints.add("recheck credentials and least-privilege policy");
        }
        if (code.contains("NOT_FOUND") || code.contains("UNRESOLVED")) {
            hints.add("verify resource identifiers and provider mapping");
        }

        if (op.type().startsWith("DOCKER_") || op.type().startsWith("K8S_")) {
            hints.add("switch provider or fallback image");
        }
        hints.add("revalidate desired state constraints");
        return hints;
    }

    private double confidenceScore(String errorCode) {
        if (errorCode == null || errorCode.isBlank()) {
            return 0.70;
        }
        String normalized = errorCode.toUpperCase();
        if (normalized.contains("NOT_FOUND") || normalized.contains("UNRESOLVED")) {
            return 0.95;
        }
        if (normalized.contains("TIMEOUT")) {
            return 0.90;
        }
        return 0.85;
    }

    private Recoverability inferRecoverability(String errorCode) {
        if (errorCode == null || errorCode.isBlank()) {
            return Recoverability.RETRYABLE;
        }
        String normalized = errorCode.toUpperCase();
        if (normalized.contains("NOT_FOUND") || normalized.contains("UNRESOLVED")) {
            return Recoverability.REPAIRABLE;
        }
        if (normalized.contains("FORBIDDEN") || normalized.contains("PERMISSION") || normalized.contains("AUTH")) {
            return Recoverability.NON_RECOVERABLE;
        }
        return Recoverability.RETRYABLE;
    }
}

