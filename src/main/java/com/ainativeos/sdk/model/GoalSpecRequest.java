package com.ainativeos.sdk.model;

import java.util.List;
import java.util.Map;

public record GoalSpecRequest(
        String goalId,
        String naturalLanguageIntent,
        List<String> successCriteria,
        Map<String, String> constraints,
        int maxRetries,
        String policyProfile
) {
}
