package com.ainativeos.kernel.policy;

import com.ainativeos.config.ExecutionPolicyProperties;
import com.ainativeos.domain.AtomicOp;
import com.ainativeos.domain.GoalPlan;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
/**
 * 简化策略引擎实现。
 * <p>
 * 在统一配置策略基础上增加高风险识别、熔断、限流与环境约束校验。
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

        if (!"default".equalsIgnoreCase(profile) && !executionPolicyProperties.getProfiles().containsKey(profile)) {
            details.put("requestedProfile", profile);
            return PolicyDecision.blocked("Unknown policy profile", "policy-profile-unknown-001", details);
        }

        RiskAssessment riskAssessment = assessRisk(plan);
        details.put("riskLevel", riskAssessment.level());
        details.put("riskReasons", riskAssessment.reasons());

        if ("strict".equalsIgnoreCase(profile) && riskAssessment.highRisk()) {
            boolean allowHighRisk = parseBoolean(plan.goalSpec().constraints(), "allowHighRisk", false);
            details.put("allowHighRisk", allowHighRisk);
            if (!allowHighRisk) {
                return PolicyDecision.blocked("Strict profile blocks high-risk goals", "policy-strict-highrisk-001", details);
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

    private RiskAssessment assessRisk(GoalPlan plan) {
        Set<String> reasons = new HashSet<>();
        Map<String, String> constraints = plan.goalSpec().constraints();
        if (constraints != null) {
            String risk = constraints.getOrDefault("risk", "low");
            if ("high".equalsIgnoreCase(risk) || "critical".equalsIgnoreCase(risk)) {
                reasons.add("risk-tag:" + risk.toLowerCase());
            }
            if (constraints.containsKey("remoteHost")) {
                reasons.add("remote-execution");
            }
            if ("true".equalsIgnoreCase(constraints.getOrDefault("requiresDocker", "false"))) {
                reasons.add("requires-docker");
            }
            if ("true".equalsIgnoreCase(constraints.getOrDefault("requiresKubectl", "false"))) {
                reasons.add("requires-kubectl");
            }
            if (constraints.containsKey("pluginId")) {
                reasons.add("plugin-execution");
            }
        }
        for (AtomicOp op : plan.atomicOps()) {
            if (op.type().startsWith("SYSTEM_")
                    || op.type().startsWith("K8S_")
                    || op.type().startsWith("CLOUD_")
                    || op.type().startsWith("DOCKER_")
                    || op.type().startsWith("PLUGIN_")) {
                reasons.add("op-type:" + op.type());
            }
        }
        List<String> ordered = new ArrayList<>(reasons);
        ordered.sort(String::compareTo);
        return new RiskAssessment(!ordered.isEmpty(), ordered);
    }

    private boolean parseBoolean(Map<String, String> constraints, String key, boolean fallback) {
        if (constraints == null) {
            return fallback;
        }
        String raw = constraints.get(key);
        if (raw == null) {
            return fallback;
        }
        return "true".equalsIgnoreCase(raw.trim());
    }

    private record RiskAssessment(boolean highRisk, List<String> reasons) {
        String level() {
            return highRisk ? "high" : "low";
        }
    }
}

