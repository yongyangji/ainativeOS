package com.ainativeos.kernel.planner;

import com.ainativeos.config.ExecutionPolicyProperties;
import com.ainativeos.domain.GoalPlan;
import com.ainativeos.domain.GoalSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultGoalPlannerTest {

    @Test
    void shouldIncludeRuntimeCommandWhenProvided() {
        ExecutionPolicyProperties policy = new ExecutionPolicyProperties();
        DefaultGoalPlanner planner = new DefaultGoalPlanner(policy);

        GoalSpec spec = new GoalSpec(
                "g1",
                "run command",
                List.of("ok"),
                Map.of("runtimeCommand", "echo test"),
                2,
                "default"
        );

        GoalPlan plan = planner.plan(spec);
        assertEquals("planner-v2", plan.plannerVersion());
        assertTrue(plan.atomicOps().stream().anyMatch(op -> "RUNTIME_APPLY_DECLARATIVE_STATE".equals(op.type())));
        assertTrue(plan.atomicOps().stream().anyMatch(op -> "echo test".equals(op.parameters().get("command"))));
    }
}
