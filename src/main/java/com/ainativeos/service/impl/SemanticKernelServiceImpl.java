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
/**
 * 语义内核服务实现。
 * <p>
 * 职责：
 * 1. 通过 planner 生成目标计划
 * 2. 通过 execution engine 执行计划
 * 3. 将执行结果与轨迹持久化到 MySQL
 */
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
        // 规划阶段仅生成计划，不产生副作用
        return goalPlanner.plan(goalSpec);
    }

    @Override
    @Transactional
    public GoalExecutionResult execute(GoalPlan plan) {
        // 执行阶段会触发能力层调用，并返回完整执行轨迹
        GoalExecutionResult result = executionEngine.run(plan);

        // 持久化执行摘要，便于后续审计与列表查询
        GoalExecutionEntity execution = new GoalExecutionEntity();
        execution.setGoalId(plan.goalSpec().goalId());
        execution.setIntent(plan.goalSpec().naturalLanguageIntent());
        execution.setStatus(result.status().name());
        execution.setSummary(result.message());
        execution.setPlannerVersion(plan.plannerVersion());
        execution.setFailureJson(toJson(result.failureObject()));
        execution.setCreatedAt(Instant.now());
        goalExecutionRepository.save(execution);

        // 持久化每一步轨迹，支持重放与排障
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
        // 失败对象序列化失败不应阻塞主流程，降级为错误标记 JSON
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
