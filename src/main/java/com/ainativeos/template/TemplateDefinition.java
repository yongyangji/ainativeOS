package com.ainativeos.template;

import java.util.List;
import java.util.Map;

public record TemplateDefinition(
        String naturalLanguageIntent,
        List<String> successCriteria,
        Map<String, String> constraints,
        Integer maxRetries,
        String policyProfile
) {
}
