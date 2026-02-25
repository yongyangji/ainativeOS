package com.ainativeos.domain;

import java.util.List;

public record OpExecutionResult(
        boolean success,
        String provider,
        String message,
        List<ContextFrame> contextFrames,
        String errorCode,
        String recommendation
) {
}
