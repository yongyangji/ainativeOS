package com.ainativeos.kernel.policy;

public record PolicyDecision(
        boolean allowed,
        String reason,
        String policyId
) {
}
