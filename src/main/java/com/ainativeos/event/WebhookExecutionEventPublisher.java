package com.ainativeos.event;

import com.ainativeos.domain.GoalExecutionResult;
import com.ainativeos.domain.GoalPlan;
import com.ainativeos.persistence.entity.EventDeliveryEntity;
import com.ainativeos.persistence.repository.EventDeliveryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class WebhookExecutionEventPublisher implements ExecutionEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(WebhookExecutionEventPublisher.class);
    private final EventProperties eventProperties;
    private final EventDeliveryRepository eventDeliveryRepository;
    private final ObjectMapper objectMapper;

    public WebhookExecutionEventPublisher(
            EventProperties eventProperties,
            EventDeliveryRepository eventDeliveryRepository,
            ObjectMapper objectMapper
    ) {
        this.eventProperties = eventProperties;
        this.eventDeliveryRepository = eventDeliveryRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publishExecutionCompleted(GoalPlan plan, GoalExecutionResult result) {
        if (!eventProperties.isEnabled()) {
            return;
        }
        List<String> urls = eventProperties.getWebhookUrls();
        if (urls == null || urls.isEmpty()) {
            return;
        }

        Map<String, Object> payload = buildPayload(plan, result);
        String body;
        try {
            body = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("Failed to serialize execution event payload: {}", e.getMessage());
            return;
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, eventProperties.getTimeoutSeconds())))
                .build();

        for (String url : urls) {
            if (url == null || url.isBlank()) {
                continue;
            }
            sendOne(client, body, url, result.goalId());
        }
    }

    private void sendOne(HttpClient client, String body, String url, String goalId) {
        EventDeliveryEntity delivery = new EventDeliveryEntity();
        delivery.setGoalId(goalId);
        delivery.setEventType("goal.execution.completed");
        delivery.setTargetUrl(url);
        delivery.setCreatedAt(Instant.now());

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(Math.max(1, eventProperties.getTimeoutSeconds())))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            boolean success = status >= 200 && status < 300;
            delivery.setSuccess(success);
            delivery.setHttpStatus(status);
            if (!success) {
                delivery.setErrorMessage("non-2xx response");
            }
        } catch (Exception e) {
            delivery.setSuccess(false);
            delivery.setErrorMessage(e.getMessage());
        }
        eventDeliveryRepository.save(delivery);
    }

    private Map<String, Object> buildPayload(GoalPlan plan, GoalExecutionResult result) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "goal.execution.completed");
        event.put("goalId", result.goalId());
        event.put("status", result.status().name());
        event.put("message", result.message());
        event.put("llmUsed", result.llmUsed());
        event.put("llmRationale", result.llmRationale() == null ? "" : result.llmRationale());
        event.put("plannerVersion", plan.plannerVersion());
        event.put("completedAt", result.completedAt());
        return event;
    }
}
