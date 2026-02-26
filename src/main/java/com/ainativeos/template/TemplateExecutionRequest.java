package com.ainativeos.template;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record TemplateExecutionRequest(
        @NotBlank String templateId,
        String version,
        String goalId,
        Map<String, String> params,
        Integer maxRetries,
        String policyProfile
) {
}
