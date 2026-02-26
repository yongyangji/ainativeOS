package com.ainativeos.persistence.repository;

import com.ainativeos.persistence.entity.EventDeliveryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventDeliveryRepository extends JpaRepository<EventDeliveryEntity, Long> {
    List<EventDeliveryEntity> findTop100ByGoalIdOrderByCreatedAtDesc(String goalId);
}
