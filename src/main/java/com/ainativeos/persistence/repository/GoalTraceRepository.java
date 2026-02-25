package com.ainativeos.persistence.repository;

import com.ainativeos.persistence.entity.GoalTraceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalTraceRepository extends JpaRepository<GoalTraceEntity, Long> {
}
