package com.ainativeos.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;
import java.util.Map;

/**
 * 用户目标规格。
 * <p>
 * 语义内核的入口对象，描述“要做什么、成功标准、约束条件、重试策略”。
 */
public record GoalSpec(
        @NotBlank String goalId,
        @NotBlank String naturalLanguageIntent,
        @NotEmpty List<String> successCriteria,
        Map<String, String> constraints,
        @PositiveOrZero int maxRetries,
        String policyProfile
) {
    public int normalizedMaxRetries() {
        return maxRetries <= 0 ? 2 : maxRetries;
    }

    public String normalizedPolicyProfile() {
        return policyProfile == null || policyProfile.isBlank() ? "default" : policyProfile;
    }
}
