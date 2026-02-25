package com.ainativeos.domain;

import java.util.Map;

public record DesiredState(
        String stateId,
        String summary,
        Map<String, Object> declaredResources
) {
}
