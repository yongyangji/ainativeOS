package com.ainativeos.persistence.repository;

import com.ainativeos.persistence.entity.TemplateVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TemplateVersionRepository extends JpaRepository<TemplateVersionEntity, Long> {
    List<TemplateVersionEntity> findByTemplateIdOrderByCreatedAtDesc(String templateId);

    Optional<TemplateVersionEntity> findByTemplateIdAndVersion(String templateId, String version);

    List<TemplateVersionEntity> findByTemplateIdAndActiveTrueOrderByCreatedAtDesc(String templateId);

    List<TemplateVersionEntity> findByActiveTrueOrderByCreatedAtDesc();

    boolean existsByTemplateIdAndVersion(String templateId, String version);
}
