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
 * 计算能力 Provider。
 * 处理 COMPUTE_* 原子操作。
 */
public class ComputeCapabilityProvider implements CapabilityProvider {

    @Override
    public boolean supports(String opType) {
        return opType.startsWith("COMPUTE_");
    }

    @Override
    public String providerName() {
        return "compute-provider";
    }

    @Override
    public List<String> advertisedOpTypes() {
        return List.of("COMPUTE_PARSE_INTENT", "COMPUTE_POLICY_EVAL", "COMPUTE_RESOLVE_CAPABILITY", "COMPUTE_VERIFY_SUCCESS");
    }

    @Override
    public OpExecutionResult execute(AtomicOp atomicOp) {
        ContextFrame frame = new ContextFrame(
                "compute",
                atomicOp.type(),
                providerName(),
                "local-java-runtime",
                Map.of("op", atomicOp.opId()),
                Instant.now()
        );
        return new OpExecutionResult(true, providerName(), "Compute operation executed", List.of(frame), null, null);
    }
}
