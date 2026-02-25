package com.ainativeos.persistence.repository;

import com.ainativeos.persistence.entity.GoalTraceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GoalTraceRepository extends JpaRepository<GoalTraceEntity, Long> {
    List<GoalTraceEntity> findByGoalIdOrderByTimestampAsc(String goalId);
}
