package com.ainativeos.kernel.policy;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TaskRateLimiterService {

    private final Map<String, Deque<Long>> profileWindow = new ConcurrentHashMap<>();
    private static final long WINDOW_MS = 60_000L;

    public boolean tryAcquire(String profile, int limitPerMinute) {
        if (limitPerMinute <= 0) {
            return true;
        }
        Deque<Long> deque = profileWindow.computeIfAbsent(profile, ignored -> new ArrayDeque<>());
        long now = System.currentTimeMillis();
        synchronized (deque) {
            while (!deque.isEmpty() && now - deque.peekFirst() > WINDOW_MS) {
                deque.pollFirst();
            }
            if (deque.size() >= limitPerMinute) {
                return false;
            }
            deque.addLast(now);
            return true;
        }
    }
}
