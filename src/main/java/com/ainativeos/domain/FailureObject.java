package com.ainativeos.domain;

import java.util.List;
import java.util.Map;

/**
 * 结构化失败对象。
 * <p>
 * 自愈与排障的核心输入，替代传统纯文本日志。
 */
public record FailureObject(
        String failureId,
        String goalId,
        String failedOpId,
        List<ContextFrame> contextStack,
        List<ErrorVector> errorVectors,
        List<String> patchHints,
        String retryToken,
        Map<String, Object> diagnostics
) {
}
