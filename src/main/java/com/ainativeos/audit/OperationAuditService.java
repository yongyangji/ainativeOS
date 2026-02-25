package com.ainativeos.audit;

import com.ainativeos.domain.AtomicOp;
import com.ainativeos.persistence.entity.ExecutionAuditEntity;
import com.ainativeos.persistence.repository.ExecutionAuditRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

/**
 * 操作审计与幂等辅助服务。
 */
@Service
public class OperationAuditService {

    private final ExecutionAuditRepository executionAuditRepository;
    private final ObjectMapper objectMapper;

    public OperationAuditService(ExecutionAuditRepository executionAuditRepository, ObjectMapper objectMapper) {
        this.executionAuditRepository = executionAuditRepository;
        this.objectMapper = objectMapper;
    }

    public boolean shouldShortCircuit(String goalId, AtomicOp op) {
        String signature = signature(op);
        return executionAuditRepository
                .findTopByGoalIdAndOpTypeAndOpSignatureAndStatusOrderByCreatedAtDesc(goalId, op.type(), signature, "SUCCEEDED")
                .isPresent();
    }

    public void record(String goalId, AtomicOp op, String status, String provider, int attempt, String message) {
        ExecutionAuditEntity entity = new ExecutionAuditEntity();
        entity.setGoalId(goalId);
        entity.setOpId(op.opId());
        entity.setOpType(op.type());
        entity.setOpSignature(signature(op));
        entity.setStatus(status);
        entity.setProvider(provider == null ? "unknown" : provider);
        entity.setAttempt(attempt);
        entity.setMessage(message == null ? "" : truncate(message, 1000));
        entity.setCreatedAt(Instant.now());
        executionAuditRepository.save(entity);
    }

    private String signature(AtomicOp op) {
        try {
            String raw = op.type() + "|" + objectMapper.writeValueAsString(op.parameters());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.substring(0, 64);
        } catch (Exception e) {
            return Integer.toHexString((op.type() + "|" + op.parameters().toString()).hashCode());
        }
    }

    private String truncate(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }
}

