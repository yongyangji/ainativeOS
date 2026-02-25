package com.ainativeos.domain;

import java.util.List;
import java.util.Map;

public record FailureObject(
        String failureId,
        String goalId,
        String failedOpId,
        List<ContextFrame> contextStack,
        List<ErrorVector> errorVectors,
        List<String> patchHints,
        String retryToken,
        Map<String, Object> diagnostics
) {
}
