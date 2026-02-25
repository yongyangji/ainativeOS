package com.ainativeos.capability.provider;

import com.ainativeos.capability.CapabilityProvider;
import com.ainativeos.domain.AtomicOp;
import com.ainativeos.domain.ContextFrame;
import com.ainativeos.domain.OpExecutionResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
/**
 * 网络能力 Provider。
 * 处理 NETWORK_* 原子操作。
 */
public class NetworkCapabilityProvider implements CapabilityProvider {

    @Override
    public boolean supports(String opType) {
        return opType.startsWith("NETWORK_");
    }

    @Override
    public String providerName() {
        return "network-provider";
    }

    @Override
    public OpExecutionResult execute(AtomicOp atomicOp) {
        ContextFrame frame = new ContextFrame(
                "network",
                atomicOp.type(),
                providerName(),
                "cross-platform-network-fabric",
                Map.of("op", atomicOp.opId()),
                Instant.now()
        );
        return new OpExecutionResult(true, providerName(), "Network operation executed", List.of(frame), null, null);
    }
}
