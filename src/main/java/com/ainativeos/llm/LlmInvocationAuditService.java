package com.ainativeos.llm;

import com.ainativeos.persistence.entity.LlmInvocationEntity;
import com.ainativeos.persistence.repository.LlmInvocationRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class LlmInvocationAuditService {

    private final LlmInvocationRepository llmInvocationRepository;

    public LlmInvocationAuditService(LlmInvocationRepository llmInvocationRepository) {
        this.llmInvocationRepository = llmInvocationRepository;
    }

    public void record(
            String goalId,
            String primaryProvider,
            String providerUsed,
            boolean fallbackUsed,
            int httpStatusCode,
            boolean success,
            long durationMs,
            String errorMessage
    ) {
        LlmInvocationEntity entity = new LlmInvocationEntity();
        entity.setGoalId(goalId);
        entity.setPrimaryProvider(primaryProvider);
        entity.setProviderUsed(providerUsed);
        entity.setFallbackUsed(fallbackUsed);
        entity.setHttpStatusCode(httpStatusCode);
        entity.setSuccess(success);
        entity.setDurationMs(durationMs);
        entity.setErrorMessage(truncate(errorMessage, 1000));
        entity.setCreatedAt(Instant.now());
        llmInvocationRepository.save(entity);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
