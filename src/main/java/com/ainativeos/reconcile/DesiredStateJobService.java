package com.ainativeos.reconcile;

import com.ainativeos.domain.AtomicOp;
import com.ainativeos.domain.GoalPlan;
import com.ainativeos.persistence.entity.DesiredStateJobEntity;
import com.ainativeos.persistence.repository.DesiredStateJobRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * 声明式状态任务服务。
 */
@Service
public class DesiredStateJobService {

    private final DesiredStateJobRepository desiredStateJobRepository;
    private final ObjectMapper objectMapper;

    public DesiredStateJobService(DesiredStateJobRepository desiredStateJobRepository, ObjectMapper objectMapper) {
        this.desiredStateJobRepository = desiredStateJobRepository;
        this.objectMapper = objectMapper;
    }

    public Optional<Long> createContinuousJobIfNeeded(GoalPlan plan) {
        if (plan.goalSpec().constraints() == null
                || !"true".equalsIgnoreCase(plan.goalSpec().constraints().getOrDefault("continuousReconcile", "false"))) {
            return Optional.empty();
        }

        Optional<AtomicOp> runtimeOpOpt = plan.atomicOps().stream()
                .filter(op -> "RUNTIME_APPLY_DECLARATIVE_STATE".equals(op.type()))
                .findFirst();
        if (runtimeOpOpt.isEmpty()) {
            return Optional.empty();
        }
        AtomicOp runtimeOp = runtimeOpOpt.get();
        String applyCommand = value(runtimeOp.parameters(), "reconcileApplyCommand");
        String verifyCommand = value(runtimeOp.parameters(), "reconcileVerifyCommand");
        if (applyCommand == null || verifyCommand == null) {
            return Optional.empty();
        }

        DesiredStateJobPayload payload = new DesiredStateJobPayload(
                plan.goalSpec().goalId(),
                applyCommand,
                verifyCommand,
                parseIntOrDefault(value(runtimeOp.parameters(), "reconcileMaxRounds"), 5),
                parseLongOrDefault(value(runtimeOp.parameters(), "reconcileIntervalMs"), 5000L),
                runtimeOp.timeoutSeconds(),
                runtimeOp.parameters()
        );

        DesiredStateJobEntity entity = new DesiredStateJobEntity();
        entity.setGoalId(plan.goalSpec().goalId());
        entity.setStatus("ACTIVE");
        entity.setFailCount(0);
        entity.setLastMessage("continuous reconcile job created");
        entity.setJobPayloadJson(toJson(payload));
        entity.setNextRunAt(Instant.now());
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        desiredStateJobRepository.save(entity);
        return Optional.ofNullable(entity.getId());
    }

    public DesiredStateJobPayload parsePayload(DesiredStateJobEntity entity) {
        try {
            return objectMapper.readValue(entity.getJobPayloadJson(), DesiredStateJobPayload.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String value(Map<String, Object> params, String key) {
        Object val = params.get(key);
        if (val instanceof String s && !s.isBlank()) {
            return s;
        }
        return null;
    }

    private int parseIntOrDefault(String val, int defaultValue) {
        if (val == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private long parseLongOrDefault(String val, long defaultValue) {
        if (val == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}

