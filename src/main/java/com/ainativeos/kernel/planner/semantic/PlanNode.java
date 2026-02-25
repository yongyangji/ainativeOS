package com.ainativeos.kernel.planner.semantic;

import java.util.Map;

/**
 * 计划图节点。
 */
public record PlanNode(
        String nodeId,
        String opType,
        String description,
        Map<String, Object> params
) {
}

