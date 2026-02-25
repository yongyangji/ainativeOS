package com.ainativeos.api.dto;

import java.time.Instant;

public record TraceEventResponse(
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
