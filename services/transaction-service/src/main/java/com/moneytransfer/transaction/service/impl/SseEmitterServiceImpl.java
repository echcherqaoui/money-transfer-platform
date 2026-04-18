package com.moneytransfer.transaction.service.impl;

import com.moneytransfer.security.jwt.JwtUtils;
import com.moneytransfer.transaction.service.ISseEmitterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class SseEmitterServiceImpl implements ISseEmitterService {

    // One emitter per userId — last connection wins (browser reconnect replaces old one)
    private final Map<UUID, SseEmitter> transferEmitters = new ConcurrentHashMap<>();

    /**
     * Register a client for transfer updates
     */
    @Override
    public SseEmitter registerTransferEmitter() {
        UUID currentUserId = JwtUtils.extractUserId();
        SseEmitter emitter = new SseEmitter(300_000L); // 5min

        // Atomic Swap: Close previous connection to prevent "Ghost" emitters
        SseEmitter oldEmitter = transferEmitters.put(currentUserId, emitter);
        if (oldEmitter != null)
            oldEmitter.complete(); // Close the previous connection

        // Precise Cleanup: Use (key, value) to avoid removing a new connection
        emitter.onCompletion(() -> {
            transferEmitters.remove(currentUserId, emitter);
            log.debug("SSE connection completed for user={}", currentUserId);
        });

        emitter.onTimeout(() -> {
            emitter.complete();
            transferEmitters.remove(currentUserId, emitter);
            log.debug("SSE connection timed out for user={}", currentUserId);

        });

        emitter.onError(e -> {
            transferEmitters.remove(currentUserId, emitter);
            log.debug("SSE connection error for user={}: {}", currentUserId, e.getMessage());
        });

        // Immediate Handshake
        try {
            emitter.send(SseEmitter.event()
                  .name("connection-confirmed")
                  .data("connected"));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }

        log.debug("SSE emitter registered for user={}", currentUserId);
        return emitter;
    }

    //"transfer-update"
    //"transfer-received"
    @Override
    public void pushToEmitter(String data,
                              UUID userId,
                              String eventName) {
        SseEmitter emitter = transferEmitters.get(userId);

        if (emitter == null) {
            log.debug("No active SSE session for user={}. Message dropped.", userId);
            return;
        }

        try {
            emitter.send(
                  SseEmitter.event()
                        .name(eventName)
                        .data(data)
            );
        } catch (IOException e) {
            log.debug("Failed to push SSE to user={}, removing emitter", userId);
            transferEmitters.remove(userId);
            emitter.completeWithError(e);
        }
    }
}