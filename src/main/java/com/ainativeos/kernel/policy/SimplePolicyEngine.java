package com.ainativeos.kernel.policy;

import com.ainativeos.domain.GoalPlan;
import org.springframework.stereotype.Component;

@Component
/**
 * 简化策略引擎实现。
 * <p>
 * 当前内置一条示例规则：
 * strict 档位下，若风险为 high，则阻断执行。
 */
public class SimplePolicyEngine implements PolicyEngine {

    @Override
    public PolicyDecision evaluate(GoalPlan plan) {
        String profile = plan.goalSpec().normalizedPolicyProfile();
        // 示例规则：严格模式下拒绝高风险任务
        if ("strict".equalsIgnoreCase(profile) && plan.goalSpec().constraints() != null) {
            String risk = plan.goalSpec().constraints().getOrDefault("risk", "low");
            if ("high".equalsIgnoreCase(risk)) {
                return new PolicyDecision(false, "Strict profile blocks high-risk goals", "policy-strict-001");
            }
        }
        return new PolicyDecision(true, "Policy checks passed", "policy-default-001");
    }
}
