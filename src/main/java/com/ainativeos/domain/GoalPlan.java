package com.ainativeos.domain;

import java.util.List;

/**
 * 目标执行计划。
 * <p>
 * 由 planner 产出，包含原始目标、期望状态与原子操作序列。
 */
public record GoalPlan(
        GoalSpec goalSpec,
        DesiredState desiredState,
        List<AtomicOp> atomicOps,
        String plannerVersion,
        boolean llmUsed
) {
}
