package com.ainativeos.reconcile;

import java.util.Map;

/**
 * 收敛任务负载。
 */
public record DesiredStateJobPayload(
        String goalId,
        String applyCommand,
        String verifyCommand,
        int maxRounds,
        long intervalMs,
        int timeoutSeconds,
        Map<String, Object> runtimeParams
) {
}

