package com.ainativeos.kernel.policy;

import com.ainativeos.domain.GoalPlan;

public interface PolicyEngine {
    PolicyDecision evaluate(GoalPlan plan);
}
