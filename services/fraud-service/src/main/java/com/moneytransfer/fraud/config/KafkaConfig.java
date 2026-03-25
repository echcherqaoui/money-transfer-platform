package com.moneytransfer.fraud.config;

import com.google.protobuf.Message;
import com.moneytransfer.contract.MoneyTransferInitiated;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
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

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        Map<String, Object> props = kafkaProperties.buildProducerProperties(null);
        props.put("key.serializer", StringSerializer.class);
        props.put("value.serializer", KafkaProtobufSerializer.class);
        
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    // ── Consumer ──────────────────────────────────────────────────

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
        factory.getContainerProperties().setAckMode(MANUAL_IMMEDIATE);

        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, MoneyTransferInitiated> moneyTransferInitiatedListenerContainerFactory(DefaultErrorHandler errorHandler) {
        return buildFactory(buildConsumerFactory(MoneyTransferInitiated.class), errorHandler);
    }
}