package com.ainativeos.kernel.planner;

import com.ainativeos.domain.AtomicOp;
import com.ainativeos.domain.DesiredState;
import com.ainativeos.domain.GoalPlan;
import com.ainativeos.domain.GoalSpec;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class DefaultGoalPlanner implements GoalPlanner {

    @Override
    public GoalPlan plan(GoalSpec goalSpec) {
        Map<String, Object> resources = new HashMap<>();
        resources.put("runtime", "immutable-container");
        resources.put("interface", "api-first-capability-fabric");
        resources.put("targetIntent", goalSpec.naturalLanguageIntent());
        DesiredState desiredState = new DesiredState("state-" + goalSpec.goalId(), "Converge declared runtime state", resources);

        List<AtomicOp> ops = new ArrayList<>();
        ops.add(new AtomicOp("op-parse", "COMPUTE_PARSE_INTENT", "Parse natural language goal into executable graph", Map.of(
                "intent", goalSpec.naturalLanguageIntent()
        ), true, false, 20));
        ops.add(new AtomicOp("op-policy", "COMPUTE_POLICY_EVAL", "Validate goal against policy profile", Map.of(
                "profile", goalSpec.normalizedPolicyProfile()
        ), true, false, 20));
        ops.add(new AtomicOp("op-capability", "COMPUTE_RESOLVE_CAPABILITY", "Resolve provider bindings across OS/cloud", Map.of(
                "constraints", goalSpec.constraints() == null ? Map.of() : goalSpec.constraints()
        ), true, false, 20));

        if (goalSpec.constraints() != null && "true".equalsIgnoreCase(goalSpec.constraints().getOrDefault("simulateFailure", "false"))) {
            ops.add(new AtomicOp("op-apply", "RUNTIME_APPLY_DECLARATIVE_STATE", "Apply desired state to runtime substrate", Map.of(
                    "state", desiredState,
                    "simulateFailure", true
            ), true, true, 60));
        } else {
            ops.add(new AtomicOp("op-apply", "RUNTIME_APPLY_DECLARATIVE_STATE", "Apply desired state to runtime substrate", Map.of(
                    "state", desiredState
            ), true, true, 60));
        }

        ops.add(new AtomicOp("op-verify", "COMPUTE_VERIFY_SUCCESS", "Evaluate success criteria", Map.of(
                "criteria", goalSpec.successCriteria()
        ), true, false, 20));

        return new GoalPlan(goalSpec, desiredState, ops, "planner-v1");
    }
}
