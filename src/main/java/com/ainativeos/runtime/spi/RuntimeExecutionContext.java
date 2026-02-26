package com.ainativeos.runtime.spi;

import java.util.Map;

public record RuntimeExecutionContext(
        Map<String, Object> parameters,
        String command,
        int timeoutSeconds
) {
    public String value(String key) {
        Object raw = parameters.get(key);
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw).trim();
        return value.isBlank() ? null : value;
    }

    public int valueAsInt(String key, int defaultValue) {
        String value = value(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }
}
