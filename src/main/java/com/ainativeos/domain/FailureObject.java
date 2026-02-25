package com.ainativeos.domain;

import java.util.List;

public record FailureObject(
        String failureId,
        String goalId,
        List<ContextFrame> contextStack,
        List<ErrorVector> errorVectors,
        List<String> patchHints,
        String retryToken
) {
}
