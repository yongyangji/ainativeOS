package com.ainativeos.kernel.policy;

import com.ainativeos.domain.GoalPlan;

/**
 * 策略引擎抽象。
 */
public interface PolicyEngine {
    /**
     * 对计划进行策略评估，返回是否允许执行。
     */
    PolicyDecision evaluate(GoalPlan plan);
}
