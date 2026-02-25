package com.ainativeos.kernel.policy;

import com.ainativeos.domain.GoalPlan;
import org.springframework.stereotype.Component;

@Component
public class SimplePolicyEngine implements PolicyEngine {

    @Override
    public PolicyDecision evaluate(GoalPlan plan) {
        String profile = plan.goalSpec().normalizedPolicyProfile();
        if ("strict".equalsIgnoreCase(profile) && plan.goalSpec().constraints() != null) {
            String risk = plan.goalSpec().constraints().getOrDefault("risk", "low");
            if ("high".equalsIgnoreCase(risk)) {
                return new PolicyDecision(false, "Strict profile blocks high-risk goals", "policy-strict-001");
            }
        }
        return new PolicyDecision(true, "Policy checks passed", "policy-default-001");
    }
}
