package com.ainativeos.sdk.model;

import java.time.Instant;

public record ExecutionTraceView(
        String goalId,
        String opId,
        String opType,
        String provider,
        String status,
        String message,
        Instant timestamp,
        int attempt
) {
}
