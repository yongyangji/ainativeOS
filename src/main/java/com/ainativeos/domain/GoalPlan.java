package com.ainativeos.domain;

import java.util.List;

public record GoalPlan(
        GoalSpec goalSpec,
        DesiredState desiredState,
        List<AtomicOp> atomicOps,
        String plannerVersion
) {
}
