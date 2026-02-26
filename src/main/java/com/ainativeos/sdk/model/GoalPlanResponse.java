package com.ainativeos.sdk.model;

import java.util.List;
import java.util.Map;

public record GoalPlanResponse(
        GoalSpecRequest goalSpec,
        DesiredStateView desiredState,
        List<AtomicOpView> atomicOps,
        String plannerVersion,
        boolean llmUsed,
        String llmRationale,
        Map<String, Object> planGraph
) {
}
