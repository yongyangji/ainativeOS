package com.ainativeos.sdk.model;

import java.util.Map;

public record AtomicOpView(
        String opId,
        String type,
        String description,
        Map<String, Object> parameters,
        boolean idempotent,
        boolean rollbackSupported,
        int timeoutSeconds
) {
}
