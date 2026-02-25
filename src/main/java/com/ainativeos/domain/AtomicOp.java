package com.ainativeos.domain;

import java.util.Map;

public record AtomicOp(
        String opId,
        String type,
        String description,
        Map<String, Object> parameters,
        boolean idempotent,
        boolean rollbackSupported
) {
}
