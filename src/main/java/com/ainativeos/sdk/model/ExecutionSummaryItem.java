package com.ainativeos.sdk.model;

import java.time.Instant;

public record ExecutionSummaryItem(
        Long id,
        String goalId,
        String status,
        String summary,
        String plannerVersion,
        Instant createdAt
) {
}
