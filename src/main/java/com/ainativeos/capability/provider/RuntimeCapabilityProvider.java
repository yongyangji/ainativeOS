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
public class RuntimeCapabilityProvider implements CapabilityProvider {

    @Override
    public boolean supports(String opType) {
        return opType.startsWith("RUNTIME_");
    }

    @Override
    public String providerName() {
        return "runtime-provider";
    }

    @Override
    public OpExecutionResult execute(AtomicOp atomicOp) {
        boolean simulateFailure = Boolean.TRUE.equals(atomicOp.parameters().get("simulateFailure"));
        ContextFrame frame = new ContextFrame(
                "runtime",
                atomicOp.type(),
                providerName(),
                "wasm-container-substrate",
                Map.of("op", atomicOp.opId()),
                Instant.now()
        );

        if (simulateFailure) {
            return new OpExecutionResult(
                    false,
                    providerName(),
                    "Runtime apply failed: unresolved dependency",
                    List.of(frame),
                    "RUNTIME_DEPENDENCY_UNRESOLVED",
                    "Patch operation parameters and retry"
            );
        }

        return new OpExecutionResult(true, providerName(), "Declarative runtime state applied", List.of(frame), null, null);
    }

    @Override
    public void rollback(AtomicOp atomicOp) {
        // no-op in current MVP; runtime rollback hook is reserved
    }
}
