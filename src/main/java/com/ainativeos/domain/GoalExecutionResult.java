package com.ainativeos.domain;

import java.time.Instant;
import java.util.List;

/**
 * 目标执行结果。
 * <p>
 * 包含终态状态、失败对象（如有）、以及完整轨迹，便于审计与调试。
 */
public record GoalExecutionResult(
        String goalId,
        ExecutionStatus status,
        String message,
        FailureObject failureObject,
        List<ExecutionTraceEntry> trace,
        Instant completedAt
) {
}
