package com.ainativeos.capability;

import com.ainativeos.domain.AtomicOp;
import com.ainativeos.domain.OpExecutionResult;

import java.util.List;
import java.util.Map;

/**
 * 能力 Provider 抽象。
 * <p>
 * 各类资源能力（文件、网络、计算、运行时）通过该接口统一接入。
 */
public interface CapabilityProvider {
    boolean supports(String opType);

    String providerName();

    OpExecutionResult execute(AtomicOp atomicOp);

    default void rollback(AtomicOp atomicOp) {
        // best-effort rollback in MVP
    }

    default List<String> advertisedOpTypes() {
        return List.of();
    }

    default Map<String, Object> metadata() {
        return Map.of();
    }
}
