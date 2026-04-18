package com.moneytransfer.transaction.config;

import com.google.protobuf.Message;
import com.moneytransfer.contract.FraudDetected;
import com.moneytransfer.contract.TransferCompleted;
import com.moneytransfer.contract.TransferFailed;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;

import java.util.HashMap;
import java.util.Map;

import static io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializerConfig.SPECIFIC_PROTOBUF_VALUE_TYPE;
import static org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL_IMMEDIATE;

@Configuration
@RequiredArgsConstructor
public class KafkaConfig {

    // KafkaProperties reads everything from application.yml
    private final KafkaProperties kafkaProperties;

    // ── Producers ─────────────────────────────────────────────────
    @Bean
    public KafkaProtobufSerializer<Message> kafkaProtobufSerializer() {
        KafkaProtobufSerializer<Message> serializer = new KafkaProtobufSerializer<>();

        // Ensure the serializer knows where the Registry is
        serializer.configure(
              new HashMap<>(kafkaProperties.getProperties()),
              false
        );

        return serializer;
    }

    // ── DLT Producer ──────────────────────────────────────────────
    @Bean
    public KafkaTemplate<String, Object> dlqKafkaTemplate() {
        return new KafkaTemplate<>(
              new DefaultKafkaProducerFactory<>(
                    kafkaProperties.buildProducerProperties(null)
              )
        );
    }

    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(KafkaTemplate<String, Object> dlqKafkaTemplate) {
        return new DeadLetterPublishingRecoverer(dlqKafkaTemplate);
    }

    @Bean
    public DefaultErrorHandler defaultErrorHandler(DeadLetterPublishingRecoverer deadLetterPublishingRecoverer) {
        return new DefaultErrorHandler(deadLetterPublishingRecoverer);
    }

    // ── Consumers ─────────────────────────────────────────────────
    // Two separate factories because specific.protobuf.value.type
    // cannot be set once in yml when consuming multiple Protobuf types.
    private <T extends Message> ConsumerFactory<String, T> buildConsumerFactory(Class<T> protoType) {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties(null);
        // Only override that cannot live in yml — type differs per consumer
        props.put(SPECIFIC_PROTOBUF_VALUE_TYPE, protoType);

        return new DefaultKafkaConsumerFactory<>(
              props,
              new StringDeserializer(),
              new ErrorHandlingDeserializer<>(new KafkaProtobufDeserializer<>())
        );
    }

    private <T> ConcurrentKafkaListenerContainerFactory<String, T> buildFactory(ConsumerFactory<String, T> consumerFactory,
                                                                                DefaultErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, T> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        // MANUAL ack
        factory.getContainerProperties().setAckMode(MANUAL_IMMEDIATE);

        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TransferCompleted> transferCompletedListenerFactory(DefaultErrorHandler errorHandler) {
        return buildFactory(buildConsumerFactory(TransferCompleted.class), errorHandler);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TransferFailed> transferFailedListenerFactory(DefaultErrorHandler errorHandler) {
        return buildFactory(buildConsumerFactory(TransferFailed.class), errorHandler);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, FraudDetected> fraudDetectedListenerFactory(DefaultErrorHandler errorHandler) {
        return buildFactory(buildConsumerFactory(FraudDetected.class), errorHandler);
    }
}