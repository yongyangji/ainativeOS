package com.ainativeos.runtime;

/**
 * 状态收敛结果。
 */
public record ReconcileResult(
        boolean success,
        int rounds,
        String message,
        CommandExecutionResult lastApplyResult,
        CommandExecutionResult lastVerifyResult
) {
}

