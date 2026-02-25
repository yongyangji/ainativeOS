package com.ainativeos.domain;

public record ErrorVector(
        String category,
        String code,
        String failingStage,
        String recoverability,
        double confidence,
        String recommendation
) {
}
