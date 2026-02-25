package com.ainativeos.api;

import com.ainativeos.api.dto.ExecutionSummaryResponse;
import com.ainativeos.api.dto.TraceEventResponse;
import com.ainativeos.domain.GoalExecutionResult;
import com.ainativeos.domain.GoalPlan;
import com.ainativeos.domain.GoalSpec;
import com.ainativeos.health.HealthCheckService;
import com.ainativeos.persistence.entity.GoalExecutionEntity;
import com.ainativeos.persistence.entity.GoalTraceEntity;
import com.ainativeos.persistence.repository.GoalExecutionRepository;
import com.ainativeos.persistence.repository.GoalTraceRepository;
import com.ainativeos.service.SemanticKernelService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/goals")
public class GoalController {

    private final SemanticKernelService semanticKernelService;
    private final GoalExecutionRepository goalExecutionRepository;
    private final GoalTraceRepository goalTraceRepository;
    private final HealthCheckService healthCheckService;

    public GoalController(
            SemanticKernelService semanticKernelService,
            GoalExecutionRepository goalExecutionRepository,
            GoalTraceRepository goalTraceRepository,
            HealthCheckService healthCheckService
    ) {
        this.semanticKernelService = semanticKernelService;
        this.goalExecutionRepository = goalExecutionRepository;
        this.goalTraceRepository = goalTraceRepository;
        this.healthCheckService = healthCheckService;
    }

    @PostMapping("/plan")
    public GoalPlan plan(@Valid @RequestBody GoalSpec goalSpec) {
        return semanticKernelService.plan(goalSpec);
    }

    @PostMapping("/execute")
    public GoalExecutionResult execute(@Valid @RequestBody GoalSpec goalSpec) {
        GoalPlan plan = semanticKernelService.plan(goalSpec);
        return semanticKernelService.execute(plan);
    }

    @GetMapping("/executions")
    public List<ExecutionSummaryResponse> executions(@RequestParam(required = false) String goalId) {
        List<GoalExecutionEntity> entities = (goalId == null || goalId.isBlank())
                ? goalExecutionRepository.findTop100ByOrderByCreatedAtDesc()
                : goalExecutionRepository.findTop50ByGoalIdOrderByCreatedAtDesc(goalId);

        return entities.stream().map(it -> new ExecutionSummaryResponse(
                it.getId(),
                it.getGoalId(),
                it.getStatus(),
                it.getSummary(),
                it.getPlannerVersion(),
                it.getCreatedAt()
        )).toList();
    }

    @GetMapping("/{goalId}/trace")
    public List<TraceEventResponse> trace(@PathVariable String goalId) {
        List<GoalTraceEntity> entities = goalTraceRepository.findByGoalIdOrderByTimestampAsc(goalId);
        return entities.stream().map(it -> new TraceEventResponse(
                it.getId(),
                it.getGoalId(),
                it.getOpId(),
                it.getOpType(),
                it.getProvider(),
                it.getStatus(),
                it.getMessage(),
                it.getAttempt(),
                it.getTimestamp()
        )).toList();
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return healthCheckService.check();
    }
}
