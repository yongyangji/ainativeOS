package com.ainativeos.kernel.planner.semantic;

import java.util.List;

/**
 * 简化计划图结构（节点有序，边隐式按顺序）。
 */
public record PlanGraph(
        List<PlanNode> nodes
) {
}

