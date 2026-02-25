package com.ainativeos.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;

public record GoalSpec(
        @NotBlank String goalId,
        @NotBlank String naturalLanguageIntent,
        @NotEmpty List<String> successCriteria,
        Map<String, String> constraints
) {
}
