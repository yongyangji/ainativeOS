package com.ainativeos.kernel.planner;

import com.ainativeos.domain.GoalPlan;
import com.ainativeos.domain.GoalSpec;

public interface GoalPlanner {
    GoalPlan plan(GoalSpec goalSpec);
}
