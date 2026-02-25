package com.ainativeos.domain;

import java.time.Instant;
import java.util.List;

public record GoalExecutionResult(
        String goalId,
        ExecutionStatus status,
        String message,
        FailureObject failureObject,
        List<ExecutionTraceEntry> trace,
        Instant completedAt
) {
}
