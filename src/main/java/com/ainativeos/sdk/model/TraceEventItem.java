package com.ainativeos.sdk.model;

import java.time.Instant;

public record TraceEventItem(
        Long id,
        String goalId,
        String opId,
        String opType,
        String provider,
        String status,
        String message,
        int attempt,
        Instant timestamp
) {
}
