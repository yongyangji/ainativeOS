package com.ainativeos.sdk;

import com.ainativeos.sdk.model.ExecutionSummaryItem;
import com.ainativeos.sdk.model.GoalExecutionResponse;
import com.ainativeos.sdk.model.GoalPlanResponse;
import com.ainativeos.sdk.model.GoalSpecRequest;
import com.ainativeos.sdk.model.TraceEventItem;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class AinativeOsClient {

    private final AinativeOsClientConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AinativeOsClient(AinativeOsClientConfig config) {
        this(config, new ObjectMapper());
    }

    public AinativeOsClient(AinativeOsClientConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.timeoutSeconds()))
                .build();
    }

    public GoalPlanResponse plan(GoalSpecRequest request) {
        return post("/api/goals/plan", request, GoalPlanResponse.class);
    }

    public GoalExecutionResponse execute(GoalSpecRequest request) {
        return post("/api/goals/execute", request, GoalExecutionResponse.class);
    }

    public List<ExecutionSummaryItem> executions(String goalId) {
        String path = "/api/goals/executions";
        if (goalId != null && !goalId.isBlank()) {
            path += "?goalId=" + encode(goalId);
        }
        return get(path, new TypeReference<List<ExecutionSummaryItem>>() {});
    }

    public List<TraceEventItem> trace(String goalId) {
        return get("/api/goals/" + encode(goalId) + "/trace", new TypeReference<List<TraceEventItem>>() {});
    }

    public List<Map<String, Object>> reconcileJobs(String goalId) {
        String path = "/api/goals/reconcile-jobs";
        if (goalId != null && !goalId.isBlank()) {
            path += "?goalId=" + encode(goalId);
        }
        return get(path, new TypeReference<List<Map<String, Object>>>() {});
    }

    public Map<String, Object> health() {
        return get("/api/goals/health", new TypeReference<Map<String, Object>>() {});
    }

    public List<Map<String, Object>> runtimeAdapters() {
        return get("/api/goals/runtime-adapters", new TypeReference<List<Map<String, Object>>>() {});
    }

    public List<Map<String, Object>> capabilities() {
        return get("/api/goals/capabilities", new TypeReference<List<Map<String, Object>>>() {});
    }

    public List<Map<String, Object>> templates() {
        return get("/api/goals/templates", new TypeReference<List<Map<String, Object>>>() {});
    }

    public List<Map<String, Object>> templateVersions(String templateId) {
        return get("/api/goals/templates/" + encode(templateId) + "/versions", new TypeReference<List<Map<String, Object>>>() {});
    }

    public Map<String, Object> executeTemplate(Map<String, Object> payload) {
        return post("/api/goals/templates/execute", payload, new TypeReference<Map<String, Object>>() {});
    }

    public Map<String, Object> rollbackTemplate(String templateId, String version) {
        return post(
                "/api/goals/templates/" + encode(templateId) + "/rollback",
                Map.of("version", version),
                new TypeReference<Map<String, Object>>() {}
        );
    }

    public List<Map<String, Object>> events(String goalId) {
        return get("/api/goals/events?goalId=" + encode(goalId), new TypeReference<List<Map<String, Object>>>() {});
    }

    private <T> T post(String path, Object body, Class<T> responseType) {
        return sendWithRetry(() -> {
            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.baseUrl() + path))
                    .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return parseResponse(response, responseType);
        });
    }

    private <T> T get(String path, TypeReference<T> ref) {
        return sendWithRetry(() -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.baseUrl() + path))
                    .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return parseResponse(response, ref);
        });
    }

    private <T> T post(String path, Object body, TypeReference<T> ref) {
        return sendWithRetry(() -> {
            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.baseUrl() + path))
                    .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return parseResponse(response, ref);
        });
    }

    private <T> T parseResponse(HttpResponse<String> response, Class<T> type) throws IOException {
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("HTTP " + status + ": " + response.body());
        }
        return objectMapper.readValue(response.body(), type);
    }

    private <T> T parseResponse(HttpResponse<String> response, TypeReference<T> ref) throws IOException {
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("HTTP " + status + ": " + response.body());
        }
        return objectMapper.readValue(response.body(), ref);
    }

    private <T> T sendWithRetry(ThrowingSupplier<T> supplier) {
        Exception lastException = null;
        for (int attempt = 0; attempt <= config.maxRetries(); attempt++) {
            try {
                return supplier.get();
            } catch (Exception e) {
                lastException = e;
                if (attempt == config.maxRetries()) {
                    break;
                }
                try {
                    Thread.sleep(config.retryBackoffMs());
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(interruptedException);
                }
            }
        }
        throw new RuntimeException("SDK request failed after retries", lastException);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
