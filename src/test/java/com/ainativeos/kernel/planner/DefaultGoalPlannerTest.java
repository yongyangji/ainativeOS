package com.ainativeos.kernel.planner;

import com.ainativeos.config.ExecutionPolicyProperties;
import com.ainativeos.domain.GoalPlan;
import com.ainativeos.domain.GoalSpec;
import com.ainativeos.llm.SemanticReasoner;
import com.ainativeos.kernel.planner.semantic.DefaultPlanVerifier;
import com.ainativeos.kernel.planner.semantic.HeuristicPlanGraphBuilder;
import com.ainativeos.kernel.planner.semantic.RuleBasedIntentParser;
import com.ainativeos.kernel.planner.semantic.SemanticPlanningEngine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultGoalPlannerTest {

    @Test
    void shouldIncludeRuntimeCommandWhenProvided() {
        ExecutionPolicyProperties policy = new ExecutionPolicyProperties();
        SemanticPlanningEngine planningEngine = new SemanticPlanningEngine(
                new RuleBasedIntentParser(),
                new HeuristicPlanGraphBuilder(),
                new DefaultPlanVerifier(),
                (SemanticReasoner) goalSpec -> java.util.Optional.empty()
        );
        DefaultGoalPlanner planner = new DefaultGoalPlanner(policy, planningEngine);

        GoalSpec spec = new GoalSpec(
                "g1",
                "run command",
                List.of("ok"),
                Map.of("runtimeCommand", "echo test"),
                2,
                "default"
        );

        GoalPlan plan = planner.plan(spec);
        assertEquals("planner-v3", plan.plannerVersion());
        assertTrue(plan.atomicOps().stream().anyMatch(op -> "RUNTIME_APPLY_DECLARATIVE_STATE".equals(op.type())));
        assertTrue(plan.atomicOps().stream().anyMatch(op -> "echo test".equals(op.parameters().get("command"))));
    }
}
