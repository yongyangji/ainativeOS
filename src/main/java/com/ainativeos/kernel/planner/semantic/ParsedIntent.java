package com.ainativeos.kernel.planner.semantic;

import java.util.List;
import java.util.Map;

/**
 * 意图解析结果。
 */
public record ParsedIntent(
        String goalId,
        String normalizedIntent,
        List<String> inferredActions,
        Map<String, String> inferredConstraints
) {
}

