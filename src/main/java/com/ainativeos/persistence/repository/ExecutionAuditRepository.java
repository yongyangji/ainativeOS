package com.ainativeos.persistence.repository;

import com.ainativeos.persistence.entity.ExecutionAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 执行审计仓储。
 */
public interface ExecutionAuditRepository extends JpaRepository<ExecutionAuditEntity, Long> {
    Optional<ExecutionAuditEntity> findTopByGoalIdAndOpTypeAndOpSignatureAndStatusOrderByCreatedAtDesc(
            String goalId,
            String opType,
            String opSignature,
            String status
    );
}

