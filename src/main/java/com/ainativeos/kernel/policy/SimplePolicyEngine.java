package com.ainativeos.kernel.policy;

import com.ainativeos.config.ExecutionPolicyProperties;
import com.ainativeos.domain.GoalPlan;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
/**
 * 简化策略引擎实现。
 * <p>
 * 当前内置一条示例规则：
 * strict 档位下，若风险为 high，则阻断执行。
 */
public class SimplePolicyEngine implements PolicyEngine {

    private final ExecutionPolicyProperties executionPolicyProperties;
    private final ExecutionCircuitBreakerService circuitBreakerService;
    private final TaskRateLimiterService taskRateLimiterService;
    private final ExecutionConstraintValidator executionConstraintValidator;

    public SimplePolicyEngine(
            ExecutionPolicyProperties executionPolicyProperties,
            ExecutionCircuitBreakerService circuitBreakerService,
            TaskRateLimiterService taskRateLimiterService,
            ExecutionConstraintValidator executionConstraintValidator
    ) {
        this.executionPolicyProperties = executionPolicyProperties;
        this.circuitBreakerService = circuitBreakerService;
        this.taskRateLimiterService = taskRateLimiterService;
        this.executionConstraintValidator = executionConstraintValidator;
    }

    @Override
    public PolicyDecision evaluate(GoalPlan plan) {
        String profile = plan.goalSpec().normalizedPolicyProfile();
        ExecutionPolicyProperties.ResolvedExecutionPolicy resolved = executionPolicyProperties.resolveProfile(profile);
        Map<String, Object> details = new HashMap<>();
        details.put("profile", resolved.profile());
        details.put("maxRetries", resolved.maxRetries());
        details.put("rollbackOnFailure", resolved.rollbackOnFailure());
        details.put("opTimeoutSeconds", resolved.opTimeoutSeconds());
        details.put("rateLimitPerMinute", resolved.rateLimitPerMinute());
        details.put("circuitBreakerFailureThreshold", resolved.circuitBreakerFailureThreshold());
        details.put("circuitBreakerOpenSeconds", resolved.circuitBreakerOpenSeconds());

        // 示例规则：严格模式下拒绝高风险任务
        if ("strict".equalsIgnoreCase(profile) && plan.goalSpec().constraints() != null) {
            String risk = plan.goalSpec().constraints().getOrDefault("risk", "low");
            if ("high".equalsIgnoreCase(risk)) {
                details.put("risk", risk);
                return PolicyDecision.blocked("Strict profile blocks high-risk goals", "policy-strict-001", details);
            }
        }

        ExecutionCircuitBreakerService.Snapshot snapshot = circuitBreakerService.snapshot(resolved.profile());
        if (snapshot.open()) {
            details.put("circuitOpenUntil", snapshot.openUntil());
            details.put("consecutiveFailures", snapshot.consecutiveFailures());
            return PolicyDecision.blocked("Circuit breaker is open for current profile", "policy-circuit-breaker-001", details);
        }

        boolean allowedByRate = taskRateLimiterService.tryAcquire(resolved.profile(), resolved.rateLimitPerMinute());
        if (!allowedByRate) {
            return PolicyDecision.blocked("Rate limit exceeded for current profile", "policy-rate-limit-001", details);
        }

        ExecutionConstraintValidator.ValidationResult validationResult = executionConstraintValidator.validate(plan.goalSpec());
        details.putAll(validationResult.details());
        if (!validationResult.valid()) {
            return PolicyDecision.blocked(validationResult.reason(), "policy-env-constraint-001", details);
        }

        return PolicyDecision.allowed("Policy checks passed", "policy-default-001", details);
    }
}
