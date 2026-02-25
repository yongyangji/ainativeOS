package com.ainativeos.service;

import com.ainativeos.domain.GoalExecutionResult;
import com.ainativeos.domain.GoalPlan;
import com.ainativeos.domain.GoalSpec;

/**
 * 语义内核服务抽象。
 */
public interface SemanticKernelService {
    /**
     * 生成目标计划。
     */
    GoalPlan plan(GoalSpec goalSpec);

    /**
     * 执行目标计划。
     */
    GoalExecutionResult execute(GoalPlan plan);
}
