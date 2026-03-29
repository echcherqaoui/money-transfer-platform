package com.moneytransfer.wallet.config;

import com.moneytransfer.exception.core.EventSecurityException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.KafkaException;
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
        backOff.setInitialInterval(1_000L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(4_000L);
        backOff.setMaxElapsedTime(30_000L); // give up after 30 seconds total


        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
              new DeadLetterPublishingRecoverer(kafkaTemplate),
              backOff
        );

        // Invalid HMAC signature — go straight to DLT
        errorHandler.addNotRetryableExceptions(EventSecurityException.class);

        return errorHandler;
    }

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        DefaultErrorHandler handler = getHandler(kafkaTemplate);

        handler.setLogLevel(KafkaException.Level.ERROR);

        handler.setRetryListeners((consumerRecord, failure, deliveryAttempt) ->
              log.warn(
                    "Retry attempt {} for record on topic: {} partition: {} offset: {}",
                    deliveryAttempt,
                    consumerRecord.topic(),
                    consumerRecord.partition(),
                    consumerRecord.offset(),
                    failure
              )
        );

        return handler;
    }
}