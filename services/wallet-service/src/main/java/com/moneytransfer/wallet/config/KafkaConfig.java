package com.moneytransfer.wallet.config;

import com.google.protobuf.Message;
import com.moneytransfer.contract.FraudDetected;
import com.moneytransfer.contract.MoneyTransferInitiated;
import com.moneytransfer.contract.TransferApproved;
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

    private final KafkaProperties kafkaProperties;

    // ── Producer ──────────────────────────────────────────────────
    @Bean
    public KafkaProtobufSerializer<Message> kafkaProtobufSerializer() {
        KafkaProtobufSerializer<Message> serializer = new KafkaProtobufSerializer<>();
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
    private <T extends Message> ConsumerFactory<String, T> buildConsumerFactory(Class<T> protoType) {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties(null);
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
        factory.getContainerProperties()
              .setAckMode(MANUAL_IMMEDIATE);

        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, MoneyTransferInitiated> moneyTransferInitiatedListenerContainerFactory(DefaultErrorHandler errorHandler) {
        return buildFactory(buildConsumerFactory(MoneyTransferInitiated.class), errorHandler);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TransferApproved> transferApprovedListenerContainerFactory(DefaultErrorHandler errorHandler) {
        return buildFactory(buildConsumerFactory(TransferApproved.class), errorHandler);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, FraudDetected> fraudDetectedListenerContainerFactory(DefaultErrorHandler errorHandler) {
        return buildFactory(buildConsumerFactory(FraudDetected.class), errorHandler);
    }
}