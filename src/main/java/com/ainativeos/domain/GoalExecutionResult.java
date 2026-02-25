package com.ainativeos.domain;

import java.time.Instant;

public record GoalExecutionResult(
        String goalId,
        String status,
        String message,
        FailureObject failureObject,
        Instant completedAt
) {
}
