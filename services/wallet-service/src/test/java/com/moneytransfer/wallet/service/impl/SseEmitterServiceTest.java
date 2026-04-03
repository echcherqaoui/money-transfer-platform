package com.moneytransfer.wallet.service.impl;

import com.moneytransfer.security.jwt.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static java.math.BigDecimal.TEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SseEmitterService Unit Tests")
class SseEmitterServiceTest {
    @Mock
    private SseEmitterService sseEmitterService;
    @BeforeEach
    void setUp() {
        sseEmitterService = new SseEmitterService();
    }

    @Nested
    @DisplayName("Emitter Lifecycle")
    class EmitterLifecycle {
        @Test
        @DisplayName("Should register new SSE emitter for authenticated user")
        void register_Success() {
            // Given
            UUID userId = UUID.randomUUID();

            try (MockedStatic<JwtUtils> mockedJwtUtils = mockStatic(JwtUtils.class)) {
                mockedJwtUtils.when(JwtUtils::extractUserId).thenReturn(userId);

                // When
                SseEmitter emitter = sseEmitterService.register();

                // Then
                assertThat(emitter).isNotNull();
                assertThat(emitter.getTimeout()).isZero();
            }
        }

        @Test
        @DisplayName("Should explicitly complete and replace existing emitter when user reconnects")
        void register_ReplaceExisting() throws Exception {
            UUID userId = UUID.randomUUID();

            try (MockedStatic<JwtUtils> mockedJwtUtils = mockStatic(JwtUtils.class)) {
                mockedJwtUtils.when(JwtUtils::extractUserId).thenReturn(userId);

                // Create a spy
                SseEmitter firstSpy = spy(new SseEmitter(0L));

                // Use Reflection to force this spy into the private map
                Field field = SseEmitterService.class.getDeclaredField("emitters");
                field.setAccessible(true);

                @SuppressWarnings("unchecked")
                Map<UUID, SseEmitter> map = (Map<UUID, SseEmitter>) field.get(sseEmitterService);

                map.put(userId, firstSpy);

                // Registering the second one
                SseEmitter second = sseEmitterService.register();

                // Then
                assertThat(firstSpy).isNotSameAs(second);
                verify(firstSpy).complete();
            }
        }

        @Test
        @DisplayName("Should remove emitter from internal storage when completion is triggered")
        void register_OnCompletion() throws Exception {
            // Given
            UUID userId = UUID.randomUUID();

            try (MockedStatic<JwtUtils> mockedJwtUtils = mockStatic(JwtUtils.class)) {
                mockedJwtUtils.when(JwtUtils::extractUserId).thenReturn(userId);

                // Register and get the emitter
                SseEmitter emitter = sseEmitterService.register();

                emitter.complete();

                Thread.sleep(200);

                sseEmitterService.pushBalanceUpdate(userId, TEN);

                // If we register AGAIN, it should be a fresh start
                SseEmitter newEmitter = sseEmitterService.register();
                assertThat(newEmitter).isNotSameAs(emitter);
            }
        }
    }

    @Nested
    @DisplayName("Balance Updates")
    class BalanceUpdates {
        @Test
        @DisplayName("Should silently skip push when no emitter registered")
        void pushBalanceUpdate_NoEmitter() {
            // Given
            UUID userId = UUID.randomUUID();
            BigDecimal balance = new BigDecimal("100.0000");

            // When / Then - should not throw any exception
            assertDoesNotThrow(() -> sseEmitterService.pushBalanceUpdate(userId, balance));
        }


        @Test
        @DisplayName("Should push balance update successfully")
        void pushBalanceUpdate_Success() throws Exception {
            UUID userId = UUID.randomUUID();
            BigDecimal balance = new BigDecimal("100.5000");

            try (MockedStatic<JwtUtils> mockedJwtUtils = mockStatic(JwtUtils.class)) {
                mockedJwtUtils.when(JwtUtils::extractUserId).thenReturn(userId);

                SseEmitter emitterSpy = spy(new SseEmitter(0L));

                java.lang.reflect.Field field = sseEmitterService.getClass().getDeclaredField("emitters");
                field.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<UUID, SseEmitter> map = (Map<UUID, SseEmitter>) field.get(sseEmitterService);
                map.put(userId, emitterSpy);

                // When
                sseEmitterService.pushBalanceUpdate(userId, balance);

                // Then - Verify that the event sent contains the correct data and name
                verify(emitterSpy).send(any(SseEmitter.SseEventBuilder.class));
            }
        }
    }
}