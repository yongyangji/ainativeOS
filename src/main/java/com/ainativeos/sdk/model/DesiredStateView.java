package com.ainativeos.sdk.model;

import java.util.Map;

public record DesiredStateView(
        String stateId,
        String summary,
        Map<String, Object> resources
) {
}
