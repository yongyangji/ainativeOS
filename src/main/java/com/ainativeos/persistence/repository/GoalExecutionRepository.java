package com.ainativeos.persistence.repository;

import com.ainativeos.persistence.entity.GoalExecutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 目标执行摘要仓储。
 */
public interface GoalExecutionRepository extends JpaRepository<GoalExecutionEntity, Long> {
    List<GoalExecutionEntity> findTop50ByGoalIdOrderByCreatedAtDesc(String goalId);

    List<GoalExecutionEntity> findTop100ByOrderByCreatedAtDesc();

    Optional<GoalExecutionEntity> findTop1ByGoalIdOrderByCreatedAtDesc(String goalId);
}
