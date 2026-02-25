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
