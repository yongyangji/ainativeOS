package com.ainativeos.runtime;

public record CommandExecutionResult(
        boolean success,
        int exitCode,
        String stdout,
        String stderr,
        long durationMs,
        String error
) {
}
