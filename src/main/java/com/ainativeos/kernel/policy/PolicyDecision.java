package com.ainativeos.kernel.policy;

import java.util.Map;

public record PolicyDecision(
        boolean allowed,
        String reason,
        String policyId,
        Map<String, Object> details
) {
    public static PolicyDecision allowed(String reason, String policyId, Map<String, Object> details) {
        return new PolicyDecision(true, reason, policyId, details);
    }

    public static PolicyDecision blocked(String reason, String policyId, Map<String, Object> details) {
        return new PolicyDecision(false, reason, policyId, details);
    }
}
