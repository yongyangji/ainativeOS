package com.ainativeos.kernel.planner.semantic;

import com.ainativeos.domain.GoalSpec;

import java.util.List;

/**
 * 计划验证器抽象。
 */
public interface PlanVerifier {
    List<String> verify(GoalSpec goalSpec, PlanGraph graph);
}

