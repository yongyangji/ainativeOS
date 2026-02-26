package com.ainativeos.event;

import com.ainativeos.domain.GoalExecutionResult;
import com.ainativeos.domain.GoalPlan;

public interface ExecutionEventPublisher {
    void publishExecutionCompleted(GoalPlan plan, GoalExecutionResult result);
}
