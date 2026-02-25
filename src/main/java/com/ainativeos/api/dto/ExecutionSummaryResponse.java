package com.ainativeos.api.dto;

import java.time.Instant;

public record ExecutionSummaryResponse(
        Long id,
        String goalId,
        String status,
        String summary,
        String plannerVersion,
        Instant createdAt
) {
}
