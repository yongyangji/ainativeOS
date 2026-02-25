package com.ainativeos.domain;

import java.util.Map;

/**
 * 原子操作定义。
 * <p>
 * 是执行引擎可调度的最小单元，强调可观测、可重试与可回滚属性。
 */
public record AtomicOp(
        String opId,
        String type,
        String description,
        Map<String, Object> parameters,
        boolean idempotent,
        boolean rollbackSupported,
        int timeoutSeconds
) {
}
