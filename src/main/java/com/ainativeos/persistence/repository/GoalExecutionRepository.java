package com.ainativeos.persistence.repository;

import com.ainativeos.persistence.entity.GoalExecutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GoalExecutionRepository extends JpaRepository<GoalExecutionEntity, Long> {
    List<GoalExecutionEntity> findTop50ByGoalIdOrderByCreatedAtDesc(String goalId);

    List<GoalExecutionEntity> findTop100ByOrderByCreatedAtDesc();
}
