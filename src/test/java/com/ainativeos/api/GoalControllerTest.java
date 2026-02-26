package com.ainativeos.api;

import com.ainativeos.domain.ExecutionStatus;
import com.ainativeos.domain.GoalExecutionResult;
import com.ainativeos.domain.GoalPlan;
import com.ainativeos.domain.GoalSpec;
import com.ainativeos.health.HealthCheckService;
import com.ainativeos.persistence.repository.DesiredStateJobRepository;
import com.ainativeos.persistence.repository.GoalExecutionRepository;
import com.ainativeos.persistence.repository.GoalTraceRepository;
import com.ainativeos.runtime.RuntimeCommandDispatcher;
import com.ainativeos.service.SemanticKernelService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoalControllerTest {

    @Mock
    private SemanticKernelService semanticKernelService;

    @Mock
    private GoalExecutionRepository goalExecutionRepository;

    @Mock
    private GoalTraceRepository goalTraceRepository;

    @Mock
    private DesiredStateJobRepository desiredStateJobRepository;

    @Mock
    private HealthCheckService healthCheckService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private RuntimeCommandDispatcher runtimeCommandDispatcher;

    @InjectMocks
    private GoalController goalController;

    @Test
    void execute_WithValidGoalSpec_ReturnsSuccessResult() {
        GoalSpec validGoalSpec = new GoalSpec(
                "goal-test-001",
                "run smoke test",
                List.of("ok"),
                Map.of("runtimeCommand", "echo hello"),
                2,
                "default"
        );

        GoalPlan mockPlan = new GoalPlan(validGoalSpec, null, List.of(), "planner-v3", false, "", Map.of());
        GoalExecutionResult expectedResult = new GoalExecutionResult(
                "goal-test-001",
                ExecutionStatus.SUCCEEDED,
                "Goal converged to desired state",
                false,
                "",
                null,
                List.of(),
                Instant.now()
        );

        when(semanticKernelService.plan(validGoalSpec)).thenReturn(mockPlan);
        when(semanticKernelService.execute(mockPlan)).thenReturn(expectedResult);

        GoalExecutionResult actualResult = goalController.execute(validGoalSpec);

        assertEquals(expectedResult.status(), actualResult.status());
        assertEquals(expectedResult.goalId(), actualResult.goalId());
        verify(semanticKernelService).plan(validGoalSpec);
        verify(semanticKernelService).execute(mockPlan);
    }

    @Test
    void execute_WhenPlanThrowsException_PropagatesError() {
        GoalSpec goalSpec = new GoalSpec(
                "goal-test-002",
                "invalid goal",
                List.of("ok"),
                Map.of(),
                1,
                "default"
        );
        String errorMsg = "Invalid goal spec";
        when(semanticKernelService.plan(goalSpec)).thenThrow(new IllegalArgumentException(errorMsg));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> goalController.execute(goalSpec)
        );

        assertEquals(errorMsg, exception.getMessage());
        verify(semanticKernelService).plan(goalSpec);
        verify(semanticKernelService, never()).execute(any(GoalPlan.class));
    }

    @Test
    void execute_WhenExecuteThrowsException_PropagatesError() {
        GoalSpec validGoalSpec = new GoalSpec(
                "goal-test-003",
                "run command",
                List.of("ok"),
                Map.of("runtimeCommand", "echo hi"),
                2,
                "default"
        );
        GoalPlan mockPlan = new GoalPlan(validGoalSpec, null, List.of(), "planner-v3", false, "", Map.of());
        String errorMsg = "Execution failed";

        when(semanticKernelService.plan(validGoalSpec)).thenReturn(mockPlan);
        when(semanticKernelService.execute(mockPlan)).thenThrow(new RuntimeException(errorMsg));

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> goalController.execute(validGoalSpec)
        );

        assertEquals(errorMsg, exception.getMessage());
        verify(semanticKernelService).plan(validGoalSpec);
        verify(semanticKernelService).execute(mockPlan);
    }
}
