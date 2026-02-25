package com.ainativeos.domain;

import java.time.Instant;
import java.util.Map;

public record ContextFrame(
        String stage,
        String capability,
        String provider,
        String environmentFingerprint,
        Map<String, String> metadata,
        Instant timestamp
) {
}
