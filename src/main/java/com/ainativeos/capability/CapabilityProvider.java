package com.ainativeos.capability;

import com.ainativeos.domain.AtomicOp;
import com.ainativeos.domain.OpExecutionResult;

public interface CapabilityProvider {
    boolean supports(String opType);

    String providerName();

    OpExecutionResult execute(AtomicOp atomicOp);

    default void rollback(AtomicOp atomicOp) {
        // best-effort rollback in MVP
    }
}
