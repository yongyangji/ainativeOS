package com.ainativeos.capability;

import com.ainativeos.domain.AtomicOp;
import com.ainativeos.domain.OpExecutionResult;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CapabilityRouter {

    private final List<CapabilityProvider> providers;

    public CapabilityRouter(List<CapabilityProvider> providers) {
        this.providers = providers;
    }

    public OpExecutionResult execute(AtomicOp op) {
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
        providers.stream()
                .filter(p -> p.supports(op.type()))
                .findFirst()
                .ifPresent(p -> p.rollback(op));
    }
}
