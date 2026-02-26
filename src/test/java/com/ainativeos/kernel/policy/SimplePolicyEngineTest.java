package com.ainativeos.kernel.policy;

import com.ainativeos.config.ExecutionPolicyProperties;
import com.ainativeos.domain.AtomicOp;
import com.ainativeos.domain.DesiredState;
import com.ainativeos.domain.GoalPlan;
import com.ainativeos.domain.GoalSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimplePolicyEngineTest {

    @Mock
    private ExecutionCircuitBreakerService circuitBreakerService;
    @Mock
    private TaskRateLimiterService taskRateLimiterService;
    @Mock
    private ExecutionConstraintValidator executionConstraintValidator;

    private SimplePolicyEngine policyEngine;

    @BeforeEach
    void setUp() {
        ExecutionPolicyProperties properties = new ExecutionPolicyProperties();
        ExecutionPolicyProperties.ProfilePolicy strict = new ExecutionPolicyProperties.ProfilePolicy();
        strict.setMaxRetries(1);
        strict.setRateLimitPerMinute(60);
        properties.setProfiles(Map.of(
                "strict", strict,
                "default", new ExecutionPolicyProperties.ProfilePolicy()
        ));
        policyEngine = new SimplePolicyEngine(properties, circuitBreakerService, taskRateLimiterService, executionConstraintValidator);
    }

    @Test
    void evaluate_shouldBlockUnknownProfile() {
        GoalPlan plan = plan(
                new GoalSpec("g1", "intent", List.of("ok"), Map.of(), 1, "custom"),
                List.of()
        );

        PolicyDecision decision = policyEngine.evaluate(plan);

        assertFalse(decision.allowed());
        assertTrue(decision.reason().contains("Unknown policy profile"));
    }

    @Test
    void evaluate_shouldBlockHighRiskInStrictProfile() {
        GoalPlan plan = plan(
                new GoalSpec("g1", "intent", List.of("ok"), Map.of("risk", "high"), 1, "strict"),
                List.of(new AtomicOp("op1", "DOCKER_RUN_IMAGE", "run", Map.of(), true, true, 30))
        );

        PolicyDecision decision = policyEngine.evaluate(plan);

        assertFalse(decision.allowed());
        assertTrue(decision.reason().contains("high-risk"));
    }

    @Test
    void evaluate_shouldAllowHighRiskWhenExplicitlyAllowed() {
        GoalPlan plan = plan(
                new GoalSpec("g1", "intent", List.of("ok"), Map.of("risk", "high", "allowHighRisk", "true"), 1, "strict"),
                List.of(new AtomicOp("op1", "DOCKER_RUN_IMAGE", "run", Map.of(), true, true, 30))
        );
        when(circuitBreakerService.snapshot("strict"))
                .thenReturn(new ExecutionCircuitBreakerService.Snapshot(false, 0, null));
        when(taskRateLimiterService.tryAcquire("strict", 60)).thenReturn(true);
        when(executionConstraintValidator.validate(plan.goalSpec()))
                .thenReturn(new ExecutionConstraintValidator.ValidationResult(true, "ok", Map.of("computeClass", "cpu")));

        PolicyDecision decision = policyEngine.evaluate(plan);

        assertTrue(decision.allowed());
    }

    @Test
    void evaluate_shouldBlockWhenCircuitOpen() {
        GoalPlan plan = plan(new GoalSpec("g1", "intent", List.of("ok"), Map.of(), 1, "default"), List.of());
        when(circuitBreakerService.snapshot("default"))
                .thenReturn(new ExecutionCircuitBreakerService.Snapshot(true, 3, Instant.now().plusSeconds(60)));

        PolicyDecision decision = policyEngine.evaluate(plan);

        assertFalse(decision.allowed());
        assertTrue(decision.reason().contains("Circuit breaker is open"));
    }

    private GoalPlan plan(GoalSpec spec, List<AtomicOp> ops) {
        return new GoalPlan(
                spec,
                new DesiredState("state-1", "summary", Map.of()),
                ops,
                "planner-v3",
                false,
                "",
                Map.of()
        );
    }
}

