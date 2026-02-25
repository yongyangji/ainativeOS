package com.ainativeos.service.impl;

import com.ainativeos.domain.ExecutionTraceEntry;
import com.ainativeos.domain.GoalExecutionResult;
import com.ainativeos.domain.GoalPlan;
import com.ainativeos.domain.GoalSpec;
import com.ainativeos.kernel.execution.SemanticExecutionEngine;
import com.ainativeos.kernel.planner.GoalPlanner;
import com.ainativeos.persistence.entity.GoalExecutionEntity;
import com.ainativeos.persistence.entity.GoalTraceEntity;
import com.ainativeos.persistence.repository.GoalExecutionRepository;
import com.ainativeos.persistence.repository.GoalTraceRepository;
import com.ainativeos.service.SemanticKernelService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class SemanticKernelServiceImpl implements SemanticKernelService {

    private final GoalPlanner goalPlanner;
    private final SemanticExecutionEngine executionEngine;
    private final GoalExecutionRepository goalExecutionRepository;
    private final GoalTraceRepository goalTraceRepository;
    private final ObjectMapper objectMapper;

    public SemanticKernelServiceImpl(
            GoalPlanner goalPlanner,
            SemanticExecutionEngine executionEngine,
            GoalExecutionRepository goalExecutionRepository,
            GoalTraceRepository goalTraceRepository,
            ObjectMapper objectMapper
    ) {
        this.goalPlanner = goalPlanner;
        this.executionEngine = executionEngine;
        this.goalExecutionRepository = goalExecutionRepository;
        this.goalTraceRepository = goalTraceRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public GoalPlan plan(GoalSpec goalSpec) {
        return goalPlanner.plan(goalSpec);
    }

    @Override
    @Transactional
    public GoalExecutionResult execute(GoalPlan plan) {
        GoalExecutionResult result = executionEngine.run(plan);

        GoalExecutionEntity execution = new GoalExecutionEntity();
        execution.setGoalId(plan.goalSpec().goalId());
        execution.setIntent(plan.goalSpec().naturalLanguageIntent());
        execution.setStatus(result.status().name());
        execution.setSummary(result.message());
        execution.setPlannerVersion(plan.plannerVersion());
        execution.setFailureJson(toJson(result.failureObject()));
        execution.setCreatedAt(Instant.now());
        goalExecutionRepository.save(execution);

        for (ExecutionTraceEntry entry : result.trace()) {
            GoalTraceEntity trace = new GoalTraceEntity();
            trace.setGoalId(entry.goalId());
            trace.setOpId(entry.opId());
            trace.setOpType(entry.opType());
            trace.setProvider(entry.provider());
            trace.setStatus(entry.status().name());
            trace.setMessage(entry.message());
            trace.setAttempt(entry.attempt());
            trace.setTimestamp(entry.timestamp());
            goalTraceRepository.save(trace);
        }

        return result;
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"serialization_failed\"}";
        }
    }
}
