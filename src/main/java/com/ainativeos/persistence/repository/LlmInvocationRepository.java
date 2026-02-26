package com.ainativeos.persistence.repository;

import com.ainativeos.persistence.entity.LlmInvocationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LlmInvocationRepository extends JpaRepository<LlmInvocationEntity, Long> {
}
