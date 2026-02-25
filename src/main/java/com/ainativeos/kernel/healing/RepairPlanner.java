package com.ainativeos.kernel.healing;

import com.ainativeos.domain.AtomicOp;
import com.ainativeos.domain.FailureObject;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
/**
 * 修复规划器。
 * <p>
 * 根据 FailureObject 生成可重试的补丁版 AtomicOp。
 * 当前实现采用轻量内存修复策略，可按需扩展更复杂的规则引擎。
 */
public class RepairPlanner {

    public AtomicOp patchForRetry(AtomicOp failedOp, FailureObject failureObject) {
        // 基于原参数复制，进行最小化补丁，避免破坏其他上下文
        Map<String, Object> patched = new HashMap<>(failedOp.parameters());
        patched.remove("simulateFailure");
        patched.put("patchedBy", "self-healing-vfs");
        patched.put("retryToken", failureObject.retryToken());
        return new AtomicOp(
                failedOp.opId(),
                failedOp.type(),
                failedOp.description() + " [patched]",
                patched,
                failedOp.idempotent(),
                failedOp.rollbackSupported(),
                failedOp.timeoutSeconds()
        );
    }
}
