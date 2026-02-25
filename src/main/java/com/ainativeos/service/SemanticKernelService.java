package com.ainativeos.service;

import com.ainativeos.domain.GoalExecutionResult;
import com.ainativeos.domain.GoalPlan;
import com.ainativeos.domain.GoalSpec;

public interface SemanticKernelService {
    GoalPlan plan(GoalSpec goalSpec);

    GoalExecutionResult execute(GoalPlan plan);
}
