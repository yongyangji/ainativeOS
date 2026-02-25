package com.ainativeos.domain;

import java.time.Instant;
import java.util.List;

public record ExecutionTraceEntry(
        String goalId,
        String opId,
        String opType,
        String provider,
        ExecutionStatus status,
        String message,
        Instant timestamp,
        int attempt,
        List<ContextFrame> contextFrames
) {
}
