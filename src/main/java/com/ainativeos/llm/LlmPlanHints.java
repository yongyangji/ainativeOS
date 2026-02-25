package com.ainativeos.llm;

import java.util.List;
import java.util.Map;

/**
 * LLM 返回的规划提示。
 */
public record LlmPlanHints(
        List<String> suggestedActions,
        Map<String, String> suggestedConstraints,
        String rationale
) {
}

