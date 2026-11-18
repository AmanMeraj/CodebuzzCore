package com.codebuzzers.codebuzzcore.security;

import com.codebuzzers.codebuzzcore.common.ApiException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginRateLimiter {

    private static final int MAX_ATTEMPTS = 10;
    private static final long WINDOW_SECONDS = 15 * 60;
    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

    public void check(String key) {
        long now = Instant.now().getEpochSecond();
        Deque<Long> deque = attempts.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && now - deque.peekFirst() > WINDOW_SECONDS) {
                deque.removeFirst();
            }
            if (deque.size() >= MAX_ATTEMPTS) {
                throw ApiException.tooMany("Too many attempts. Try again later.");
            }
            deque.addLast(now);
        }
    }
}
