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

    public OpenAiSemanticReasoner(LlmProperties llmProperties, ObjectMapper objectMapper) {
        this.llmProperties = llmProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<LlmPlanHints> reason(GoalSpec goalSpec) {
        if (!llmProperties.isEnabled() || llmProperties.getApiKey() == null || llmProperties.getApiKey().isBlank()) {
            log.debug("LLM disabled or API key missing. enabled={}, provider={}",
                    llmProperties.isEnabled(), llmProperties.getProvider());
            return Optional.empty();
        }
        try {
            String schemaPrompt = """
                    Return strict JSON with keys:
                    suggestedActions: string[],
                    suggestedConstraints: object<string,string>,
                    rationale: string
                    """;
            String userPrompt = "GoalSpec=" + objectMapper.writeValueAsString(goalSpec);
            String body = objectMapper.writeValueAsString(Map.of(
                    "model", llmProperties.getModel(),
                    "messages", List.of(
                            Map.of("role", "system", "content", schemaPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    ),
                    "temperature", 0.1
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(llmProperties.getEndpoint()))
                    .header("Authorization", "Bearer " + llmProperties.getApiKey())
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
                        llmProperties.getProvider(),
                        response.statusCode(),
                        truncate(response.body(), 300));
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            if (contentNode.isMissingNode() || contentNode.asText().isBlank()) {
                log.warn("LLM response missing content. provider={}", llmProperties.getProvider());
                return Optional.empty();
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
                    llmProperties.getProvider(),
                    llmProperties.getModel(),
                    actions == null ? 0 : actions.size(),
                    constraints == null ? 0 : constraints.size());
            return Optional.of(new LlmPlanHints(actions == null ? List.of() : actions, constraints == null ? Map.of() : constraints, rationale));
        } catch (Exception e) {
            log.warn("LLM request exception. provider={}, message={}",
                    llmProperties.getProvider(), e.getMessage());
            return Optional.empty();
        }
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
