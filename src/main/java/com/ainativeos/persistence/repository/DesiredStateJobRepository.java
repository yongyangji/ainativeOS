package com.ainativeos.persistence.repository;

import com.ainativeos.persistence.entity.DesiredStateJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

/**
 * 声明式状态任务仓储。
 */
public interface DesiredStateJobRepository extends JpaRepository<DesiredStateJobEntity, Long> {
    List<DesiredStateJobEntity> findTop20ByStatusInAndNextRunAtBeforeOrderByNextRunAtAsc(List<String> statuses, Instant nextRunAt);

    List<DesiredStateJobEntity> findTop100ByOrderByUpdatedAtDesc();

    List<DesiredStateJobEntity> findTop100ByGoalIdOrderByUpdatedAtDesc(String goalId);
}
