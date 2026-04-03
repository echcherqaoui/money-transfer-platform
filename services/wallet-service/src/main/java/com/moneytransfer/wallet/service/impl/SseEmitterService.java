package com.moneytransfer.wallet.service.impl;

import com.moneytransfer.security.jwt.JwtUtils;
import com.moneytransfer.wallet.dto.BalanceUpdateEvent;
import com.moneytransfer.wallet.service.ISseEmitterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class SseEmitterService implements ISseEmitterService {

    // One emitter per userId — last connection wins (browser reconnect replaces old one)
    private final Map<UUID, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * Creates and registers an SSE emitter for the given user.
     * Timeout = 0 means no server-side timeout — client reconnects on drop.
     */
    @Override
    public SseEmitter register() {
        UUID currentUserId = JwtUtils.extractUserId();
        SseEmitter emitter = new SseEmitter(0L);

        // Check if there's an existing connection
        SseEmitter oldEmitter = emitters.put(currentUserId, emitter);
        if (oldEmitter != null)
            oldEmitter.complete(); // Close the previous connection

        emitter.onCompletion(() -> {
            emitters.remove(currentUserId, emitter);
            log.debug("SSE connection completed for user={}", currentUserId);
        });

        emitter.onTimeout(() -> {
            emitters.remove(currentUserId);
            log.debug("SSE connection timed out for user={}", currentUserId);
        });

        emitter.onError(e -> {
            emitters.remove(currentUserId);
            log.debug("SSE connection error for user={}: {}", currentUserId, e.getMessage());
        });

        emitters.put(currentUserId, emitter);
        log.debug("SSE emitter registered for user={}", currentUserId);

        return emitter;
    }

    /**
     * Pushes a balance update to the connected user.
     * Silently skips if the user has no active SSE connection.
     */
    @Override
    public void pushBalanceUpdate(UUID userId, BigDecimal balance) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter != null) {
            try {
                emitter.send(
                      SseEmitter.event()
                            .name("balance")
                            .data(new BalanceUpdateEvent(userId, balance))
                );
            } catch (IOException | IllegalStateException e) {
                log.debug("Failed to push SSE to user={}, removing emitter", userId);
                emitters.remove(userId);
                emitter.completeWithError(e);
            }
        }
    }
}