package com.ainativeos.domain;

public record ErrorVector(
        String category,
        String code,
        String failingStage,
        Recoverability recoverability,
        double confidence,
        String recommendation
) {
}
