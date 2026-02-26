package com.ainativeos.sdk.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record GoalExecutionResponse(
        String goalId,
        String status,
        String message,
        boolean llmUsed,
        String llmRationale,
        Map<String, Object> failureObject,
        List<ExecutionTraceView> trace,
        Instant completedAt
) {
}
