package com.ainativeos.template;

import com.ainativeos.domain.GoalExecutionResult;
import com.ainativeos.domain.GoalPlan;
import com.ainativeos.domain.GoalSpec;
import com.ainativeos.persistence.entity.TemplateVersionEntity;
import com.ainativeos.persistence.repository.TemplateVersionRepository;
import com.ainativeos.service.SemanticKernelService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class TemplateService {

    private final TemplateVersionRepository templateVersionRepository;
    private final SemanticKernelService semanticKernelService;
    private final ObjectMapper objectMapper;
    private final TemplateProperties templateProperties;

    public TemplateService(
            TemplateVersionRepository templateVersionRepository,
            SemanticKernelService semanticKernelService,
            ObjectMapper objectMapper,
            TemplateProperties templateProperties
    ) {
        this.templateVersionRepository = templateVersionRepository;
        this.semanticKernelService = semanticKernelService;
        this.objectMapper = objectMapper;
        this.templateProperties = templateProperties;
    }

    @PostConstruct
    @Transactional
    public void initBuiltInTemplates() {
        if (!templateProperties.isEnabled()) {
            return;
        }
        upsertBuiltIn("tpl-deploy-service", "Service Deployment", "deployment", "1.0.0",
                "Deploy app container and verify status",
                new TemplateDefinition(
                        "Deploy application service",
                        List.of("deployment_applied", "deployment_verified"),
                        Map.of(
                                "runtimeCommand", "{{runtimeCommand}}",
                                "requiresDocker", "true",
                                "fallbackCommand", "{{fallbackCommand}}"
                        ),
                        2,
                        "default"
                ));

        upsertBuiltIn("tpl-inspect-runtime", "Runtime Inspection", "inspection", "1.0.0",
                "Inspect runtime host and dependencies",
                new TemplateDefinition(
                        "Inspect runtime environment",
                        List.of("runtime_visible"),
                        Map.of("runtimeCommand", "{{runtimeCommand}}"),
                        1,
                        "default"
                ));

        upsertBuiltIn("tpl-self-heal-runtime", "Runtime Self-Heal", "self-healing", "1.0.0",
                "Apply and verify desired state with retries",
                new TemplateDefinition(
                        "Self-heal runtime workload",
                        List.of("state_converged"),
                        Map.of(
                                "runtimeCommand", "{{runtimeCommand}}",
                                "reconcileApplyCommand", "{{reconcileApplyCommand}}",
                                "reconcileVerifyCommand", "{{reconcileVerifyCommand}}",
                                "reconcileMaxRounds", "{{reconcileMaxRounds}}",
                                "reconcileIntervalMs", "{{reconcileIntervalMs}}"
                        ),
                        3,
                        "strict"
                ));
    }

    public List<Map<String, Object>> listActiveTemplates() {
        return templateVersionRepository.findByActiveTrueOrderByCreatedAtDesc().stream()
                .map(this::toSummary)
                .toList();
    }

    public List<Map<String, Object>> listVersions(String templateId) {
        return templateVersionRepository.findByTemplateIdOrderByCreatedAtDesc(templateId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public Map<String, Object> rollback(String templateId, String version) {
        TemplateVersionEntity target = templateVersionRepository.findByTemplateIdAndVersion(templateId, version)
                .orElseThrow(() -> new IllegalArgumentException("Template version not found"));
        List<TemplateVersionEntity> versions = templateVersionRepository.findByTemplateIdOrderByCreatedAtDesc(templateId);
        for (TemplateVersionEntity item : versions) {
            item.setActive(item.getId().equals(target.getId()));
            templateVersionRepository.save(item);
        }
        return toSummary(target);
    }

    public GoalExecutionResult execute(TemplateExecutionRequest request) {
        TemplateVersionEntity template = resolveTemplate(request.templateId(), request.version());
        TemplateDefinition definition = parseDefinition(template);
        GoalSpec goalSpec = renderGoalSpec(request, definition, template);
        GoalPlan plan = semanticKernelService.plan(goalSpec);
        return semanticKernelService.execute(plan);
    }

    private void upsertBuiltIn(
            String templateId,
            String name,
            String category,
            String version,
            String description,
            TemplateDefinition definition
    ) {
        if (templateVersionRepository.existsByTemplateIdAndVersion(templateId, version)) {
            return;
        }
        TemplateVersionEntity entity = new TemplateVersionEntity();
        entity.setTemplateId(templateId);
        entity.setName(name);
        entity.setCategory(category);
        entity.setVersion(version);
        entity.setDescription(description);
        entity.setTemplateJson(toJson(definition));
        entity.setBuiltIn(true);
        entity.setActive(true);
        entity.setCreatedAt(Instant.now());

        List<TemplateVersionEntity> existing = templateVersionRepository.findByTemplateIdOrderByCreatedAtDesc(templateId);
        for (TemplateVersionEntity e : existing) {
            if (e.isActive()) {
                e.setActive(false);
                templateVersionRepository.save(e);
            }
        }
        templateVersionRepository.save(entity);
    }

    private TemplateVersionEntity resolveTemplate(String templateId, String version) {
        if (version != null && !version.isBlank()) {
            return templateVersionRepository.findByTemplateIdAndVersion(templateId, version)
                    .orElseThrow(() -> new IllegalArgumentException("Template version not found"));
        }
        return templateVersionRepository.findByTemplateIdAndActiveTrueOrderByCreatedAtDesc(templateId).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Active template not found"));
    }

    private GoalSpec renderGoalSpec(TemplateExecutionRequest request, TemplateDefinition definition, TemplateVersionEntity template) {
        Map<String, String> params = request.params() == null ? Map.of() : request.params();
        String goalId = request.goalId();
        if (goalId == null || goalId.isBlank()) {
            goalId = request.templateId() + "-" + UUID.randomUUID().toString().substring(0, 8);
        }

        String intent = replaceVars(definition.naturalLanguageIntent(), params);
        List<String> successCriteria = definition.successCriteria() == null ? List.of("ok")
                : definition.successCriteria().stream().map(item -> replaceVars(item, params)).toList();

        Map<String, String> constraints = new HashMap<>();
        if (definition.constraints() != null) {
            definition.constraints().forEach((k, v) -> {
                String resolved = replaceVars(v, params);
                if (resolved != null && !resolved.isBlank()) {
                    constraints.put(k, resolved);
                }
            });
        }
        constraints.putIfAbsent("templateId", template.getTemplateId());
        constraints.putIfAbsent("templateVersion", template.getVersion());

        int maxRetries = request.maxRetries() != null ? Math.max(0, request.maxRetries())
                : (definition.maxRetries() == null ? 2 : Math.max(0, definition.maxRetries()));
        String policy = request.policyProfile() == null || request.policyProfile().isBlank()
                ? (definition.policyProfile() == null ? "default" : definition.policyProfile())
                : request.policyProfile();

        return new GoalSpec(goalId, intent, successCriteria, constraints, maxRetries, policy);
    }

    private String replaceVars(String raw, Map<String, String> params) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String result = raw;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = "{{" + entry.getKey() + "}}";
            result = result.replace(key, entry.getValue() == null ? "" : entry.getValue());
        }
        return result.replaceAll("\\{\\{[^}]+}}", "");
    }

    private TemplateDefinition parseDefinition(TemplateVersionEntity entity) {
        try {
            return objectMapper.readValue(entity.getTemplateJson(), TemplateDefinition.class);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid template definition json", e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Serialize template failed", e);
        }
    }

    private Map<String, Object> toSummary(TemplateVersionEntity entity) {
        Map<String, Object> item = new HashMap<>();
        item.put("templateId", entity.getTemplateId());
        item.put("name", entity.getName());
        item.put("category", entity.getCategory());
        item.put("version", entity.getVersion());
        item.put("description", entity.getDescription());
        item.put("builtIn", entity.isBuiltIn());
        item.put("active", entity.isActive());
        item.put("createdAt", entity.getCreatedAt());
        return item;
    }
}
