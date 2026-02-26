package com.ainativeos.sdk;

public record AinativeOsClientConfig(
        String baseUrl,
        int timeoutSeconds,
        int maxRetries,
        long retryBackoffMs
) {
    public AinativeOsClientConfig {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }
        if (timeoutSeconds <= 0) {
            timeoutSeconds = 20;
        }
        if (maxRetries < 0) {
            maxRetries = 1;
        }
        if (retryBackoffMs < 0) {
            retryBackoffMs = 200;
        }
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
    }

    public static AinativeOsClientConfig defaults(String baseUrl) {
        return new AinativeOsClientConfig(baseUrl, 20, 1, 200);
    }
}
