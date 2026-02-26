package com.ainativeos.kernel.planner.semantic;

import com.ainativeos.domain.AtomicOp;

import java.util.List;

/**
 * 规划蓝图。
 */
public record PlanningBlueprint(
        List<AtomicOp> preRuntimeOps,
        List<String> warnings,
        boolean llmUsed,
        String llmRationale,
        PlanGraph planGraph
) {
}
