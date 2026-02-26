package com.ainativeos.llm;

import com.ainativeos.domain.GoalSpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * OpenAI 语义推理器。
 * <p>
 * 若配置不完整或请求失败，返回 Optional.empty()，由规则引擎兜底。
 */
@Component
public class OpenAiSemanticReasoner implements SemanticReasoner {

    private static final Logger log = LoggerFactory.getLogger(OpenAiSemanticReasoner.class);
    private final LlmProperties llmProperties;
    private final ObjectMapper objectMapper;
    private final LlmInvocationAuditService llmInvocationAuditService;

    public OpenAiSemanticReasoner(
            LlmProperties llmProperties,
            ObjectMapper objectMapper,
            LlmInvocationAuditService llmInvocationAuditService
    ) {
        this.llmProperties = llmProperties;
        this.objectMapper = objectMapper;
        this.llmInvocationAuditService = llmInvocationAuditService;
    }

    @Override
    public Optional<LlmPlanHints> reason(GoalSpec goalSpec) {
        if (!llmProperties.isEnabled() || llmProperties.getApiKey() == null || llmProperties.getApiKey().isBlank()) {
            log.debug("LLM disabled or API key missing. enabled={}, provider={}",
                    llmProperties.isEnabled(), llmProperties.getProvider());
            return Optional.empty();
        }
        long start = System.currentTimeMillis();
        ProviderConfig primary = ProviderConfig.primary(llmProperties);
        ProviderResult primaryResult = callProvider(goalSpec, primary);
        if (primaryResult.success()) {
            llmInvocationAuditService.record(
                    goalSpec.goalId(),
                    primary.provider(),
                    primary.provider(),
                    false,
                    primaryResult.statusCode(),
                    true,
                    System.currentTimeMillis() - start,
                    ""
            );
            return Optional.of(primaryResult.hints());
        }

        ProviderConfig fallback = ProviderConfig.fallback(llmProperties);
        if (fallback.isConfigured()) {
            ProviderResult fallbackResult = callProvider(goalSpec, fallback);
            boolean fallbackSuccess = fallbackResult.success();
            llmInvocationAuditService.record(
                    goalSpec.goalId(),
                    primary.provider(),
                    fallback.provider(),
                    true,
                    fallbackResult.statusCode(),
                    fallbackSuccess,
                    System.currentTimeMillis() - start,
                    fallbackResult.errorMessage()
            );
            if (fallbackSuccess) {
                return Optional.of(fallbackResult.hints());
            }
            return Optional.empty();
        }

        llmInvocationAuditService.record(
                goalSpec.goalId(),
                primary.provider(),
                primary.provider(),
                false,
                primaryResult.statusCode(),
                false,
                System.currentTimeMillis() - start,
                primaryResult.errorMessage()
        );
        return Optional.empty();
    }

    private ProviderResult callProvider(GoalSpec goalSpec, ProviderConfig config) {
        try {
            String schemaPrompt = """
                    Return strict JSON with keys:
                    suggestedActions: string[],
                    suggestedConstraints: object<string,string>,
                    rationale: string
                    """;
            String userPrompt = "GoalSpec=" + objectMapper.writeValueAsString(goalSpec);
            String body = objectMapper.writeValueAsString(Map.of(
                    "model", config.model(),
                    "messages", List.of(
                            Map.of("role", "system", "content", schemaPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    ),
                    "temperature", 0.1
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.endpoint()))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(llmProperties.getTimeoutSeconds()))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(llmProperties.getTimeoutSeconds()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("LLM request failed. provider={}, status={}, body={}",
                        config.provider(),
                        response.statusCode(),
                        truncate(response.body(), 300));
                return ProviderResult.failure(response.statusCode(), "http_non_2xx");
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            if (contentNode.isMissingNode() || contentNode.asText().isBlank()) {
                log.warn("LLM response missing content. provider={}", config.provider());
                return ProviderResult.failure(response.statusCode(), "empty_content");
            }
            JsonNode hintNode = objectMapper.readTree(contentNode.asText());
            List<String> actions = objectMapper.convertValue(
                    hintNode.path("suggestedActions"),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
            Map<String, String> constraints = objectMapper.convertValue(
                    hintNode.path("suggestedConstraints"),
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, String.class)
            );
            String rationale = hintNode.path("rationale").asText("");
            log.info("LLM hints merged. provider={}, model={}, actions={}, constraints={}",
                    config.provider(),
                    config.model(),
                    actions == null ? 0 : actions.size(),
                    constraints == null ? 0 : constraints.size());
            return ProviderResult.success(
                    response.statusCode(),
                    new LlmPlanHints(actions == null ? List.of() : actions, constraints == null ? Map.of() : constraints, rationale)
            );
        } catch (Exception e) {
            log.warn("LLM request exception. provider={}, message={}",
                    config.provider(), e.getMessage());
            return ProviderResult.failure(-1, e.getMessage());
        }
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    private record ProviderConfig(
            String provider,
            String endpoint,
            String apiKey,
            String model
    ) {
        static ProviderConfig primary(LlmProperties props) {
            return new ProviderConfig(
                    blankDefault(props.getProvider(), "openai"),
                    blankDefault(props.getEndpoint(), "https://api.openai.com/v1/chat/completions"),
                    blankDefault(props.getApiKey(), ""),
                    blankDefault(props.getModel(), "gpt-4o-mini")
            );
        }

        static ProviderConfig fallback(LlmProperties props) {
            return new ProviderConfig(
                    blankDefault(props.getFallbackProvider(), ""),
                    blankDefault(props.getFallbackEndpoint(), ""),
                    blankDefault(props.getFallbackApiKey(), ""),
                    blankDefault(props.getFallbackModel(), "")
            );
        }

        boolean isConfigured() {
            return !provider.isBlank() && !endpoint.isBlank() && !apiKey.isBlank() && !model.isBlank();
        }

        private static String blankDefault(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value;
        }
    }

    private record ProviderResult(
            boolean success,
            int statusCode,
            String errorMessage,
            LlmPlanHints hints
    ) {
        static ProviderResult success(int statusCode, LlmPlanHints hints) {
            return new ProviderResult(true, statusCode, "", hints);
        }

        static ProviderResult failure(int statusCode, String errorMessage) {
            return new ProviderResult(false, statusCode, errorMessage, null);
        }
    }
}
