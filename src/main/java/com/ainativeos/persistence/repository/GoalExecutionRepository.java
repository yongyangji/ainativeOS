package com.ainativeos.persistence.repository;

import com.ainativeos.persistence.entity.GoalExecutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalExecutionRepository extends JpaRepository<GoalExecutionEntity, Long> {
}
