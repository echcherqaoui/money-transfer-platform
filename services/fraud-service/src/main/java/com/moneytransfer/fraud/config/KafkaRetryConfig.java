package com.moneytransfer.fraud.config;

import com.moneytransfer.exception.core.EventSecurityException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class KafkaRetryConfig {

    private static DefaultErrorHandler getHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        ExponentialBackOff backOff = new ExponentialBackOff();
        backOff.setInitialInterval(1_000L);    // 1s
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(4_000L);        // Increased to allow 1s, 2s, 4s progression
        backOff.setMaxElapsedTime(20_000L);     // give up after 20 seconds total

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        // Invalid HMAC signature — go straight to DLT
        errorHandler.addNotRetryableExceptions(EventSecurityException.class);

        return errorHandler;
    }

    // ─── Error Handler — Exponential Backoff + DLT ───────────────
    // After exhaustion: publishes to <topic>.DLT
    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        DefaultErrorHandler handler = getHandler(kafkaTemplate);

        handler.setRetryListeners((consumerRecord, failure, deliveryAttempt) ->
              log.warn(
                    "Retry attempt {} for record on topic: {} partition: {} offset: {} error: {}",
                    deliveryAttempt,
                    consumerRecord.topic(),
                    consumerRecord.partition(),
                    consumerRecord.offset(),
                    failure.getMessage()
              )
        );

        return handler;
    }
}