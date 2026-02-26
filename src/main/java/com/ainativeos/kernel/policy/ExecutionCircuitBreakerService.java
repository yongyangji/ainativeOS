package com.ainativeos.kernel.policy;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ExecutionCircuitBreakerService {

    private final Map<String, CircuitState> states = new ConcurrentHashMap<>();

    public Snapshot snapshot(String profile) {
        CircuitState state = states.computeIfAbsent(profile, ignored -> new CircuitState());
        synchronized (state) {
            boolean open = state.openUntilEpochMs > System.currentTimeMillis();
            Instant openUntil = open ? Instant.ofEpochMilli(state.openUntilEpochMs) : null;
            return new Snapshot(open, state.consecutiveFailures, openUntil);
        }
    }

    public void recordSuccess(String profile) {
        CircuitState state = states.computeIfAbsent(profile, ignored -> new CircuitState());
        synchronized (state) {
            state.consecutiveFailures = 0;
            state.openUntilEpochMs = 0L;
        }
    }

    public void recordFailure(String profile, int threshold, int openSeconds) {
        CircuitState state = states.computeIfAbsent(profile, ignored -> new CircuitState());
        synchronized (state) {
            state.consecutiveFailures++;
            if (state.consecutiveFailures >= threshold) {
                state.openUntilEpochMs = System.currentTimeMillis() + (long) openSeconds * 1000L;
            }
        }
    }

    private static class CircuitState {
        private int consecutiveFailures = 0;
        private long openUntilEpochMs = 0L;
    }

    public record Snapshot(
            boolean open,
            int consecutiveFailures,
            Instant openUntil
    ) {
    }
}
