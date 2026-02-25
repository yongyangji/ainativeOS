package com.ainativeos.service.impl;

import com.ainativeos.domain.AtomicOp;
import com.ainativeos.domain.DesiredState;
import com.ainativeos.domain.GoalExecutionResult;
import com.ainativeos.domain.GoalPlan;
import com.ainativeos.domain.GoalSpec;
import com.ainativeos.persistence.entity.GoalExecutionEntity;
import com.ainativeos.persistence.repository.GoalExecutionRepository;
import com.ainativeos.service.SemanticKernelService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class SemanticKernelServiceImpl implements SemanticKernelService {

    private final GoalExecutionRepository goalExecutionRepository;

    public SemanticKernelServiceImpl(GoalExecutionRepository goalExecutionRepository) {
        this.goalExecutionRepository = goalExecutionRepository;
    }

    @Override
    public GoalPlan plan(GoalSpec goalSpec) {
        DesiredState desiredState = new DesiredState(
                "state-" + goalSpec.goalId(),
                "Converge environment to satisfy goal",
                Map.of(
                        "runtime", "wasm_or_container",
                        "api_mode", "capability_fabric"
                )
        );

        List<AtomicOp> atomicOps = List.of(
                new AtomicOp("op-1", "PARSE_INTENT", "Parse natural language into executable intent", Map.of(), true, false),
                new AtomicOp("op-2", "RESOLVE_CAPABILITIES", "Map abstract resources to concrete providers", Map.of(), true, false),
                new AtomicOp("op-3", "APPLY_DECLARATIVE_STATE", "Apply desired state in immutable runtime", Map.of(), true, true),
                new AtomicOp("op-4", "VERIFY_SUCCESS", "Verify success criteria and emit trace", Map.of(), true, false)
        );

        return new GoalPlan(goalSpec, desiredState, atomicOps);
    }

    @Override
    public GoalExecutionResult execute(GoalPlan plan) {
        GoalExecutionEntity entity = new GoalExecutionEntity();
        entity.setGoalId(plan.goalSpec().goalId());
        entity.setIntent(plan.goalSpec().naturalLanguageIntent());
        entity.setStatus("SUCCEEDED");
        entity.setSummary("Goal executed in dry-run orchestration mode");
        entity.setCreatedAt(Instant.now());
        goalExecutionRepository.save(entity);

        return new GoalExecutionResult(
                plan.goalSpec().goalId(),
                "SUCCEEDED",
                "Semantic Kernel executed plan with " + plan.atomicOps().size() + " atomic operations",
                null,
                Instant.now()
        );
    }
}
