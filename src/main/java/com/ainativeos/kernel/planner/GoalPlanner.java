package com.ainativeos.kernel.planner;

import com.ainativeos.domain.GoalPlan;
import com.ainativeos.domain.GoalSpec;

/**
 * 目标规划器抽象。
 */
public interface GoalPlanner {
    /**
     * 将 GoalSpec 转换为可执行 GoalPlan。
     */
    GoalPlan plan(GoalSpec goalSpec);
}
