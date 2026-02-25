package com.ainativeos.capability;

import com.ainativeos.domain.AtomicOp;
import com.ainativeos.domain.OpExecutionResult;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
/**
 * 能力路由器。
 * <p>
 * 根据原子操作类型（opType）分派到具体 Provider。
 * 例如：
 * - COMPUTE_* -> ComputeCapabilityProvider
 * - RUNTIME_* -> RuntimeCapabilityProvider
 */
public class CapabilityRouter {

    private final List<CapabilityProvider> providers;

    public CapabilityRouter(List<CapabilityProvider> providers) {
        this.providers = providers;
    }

    public OpExecutionResult execute(AtomicOp op) {
        // 找到第一个可处理该 opType 的 provider 并执行
        return providers.stream()
                .filter(p -> p.supports(op.type()))
                .findFirst()
                .map(p -> p.execute(op))
                .orElseGet(() -> new OpExecutionResult(
                        false,
                        "none",
                        "No provider found for operation type " + op.type(),
                        List.of(),
                        "PROVIDER_NOT_FOUND",
                        "Register capability provider for operation type"
                ));
    }

    public void rollback(AtomicOp op) {
        // 回滚同样按 supports(opType) 进行分发
        providers.stream()
                .filter(p -> p.supports(op.type()))
                .findFirst()
                .ifPresent(p -> p.rollback(op));
    }
}
