package com.ainativeos.kernel.planner.semantic;

import com.ainativeos.domain.GoalSpec;

/**
 * 计划图构建器抽象。
 */
public interface PlanGraphBuilder {
    PlanGraph build(GoalSpec goalSpec, ParsedIntent parsedIntent);
}

