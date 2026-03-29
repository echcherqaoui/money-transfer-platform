package com.moneytransfer.fraud.service.impl;

import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import com.moneytransfer.contract.FraudDetected;
import com.moneytransfer.contract.TransferApproved;
import com.moneytransfer.fraud.exception.KafkaPublishException;
import com.moneytransfer.fraud.service.IFraudEventProducer;
import com.moneytransfer.security.service.ISignatureService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.errors.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static com.moneytransfer.fraud.exception.enums.FraudErrorCode.KAFKA_PUBLISH_FAILURE;
import static com.moneytransfer.fraud.exception.enums.FraudErrorCode.KAFKA_PUBLISH_INTERRUPTED;
import static com.moneytransfer.fraud.exception.enums.FraudErrorCode.KAFKA_PUBLISH_TIMEOUT;

@Service
@Slf4j
public class KafkaFraudEventProducer implements IFraudEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ISignatureService signatureService;
    private final String transferApprovedTopic;
    private final String fraudDetectedTopic;

    public KafkaFraudEventProducer(KafkaTemplate<String, Object> kafkaTemplate,
                                   ISignatureService signatureService,
                                   @Value("${kafka.topics.fraud.transfer-approved}") String transferApprovedTopic,
                                   @Value("${kafka.topics.fraud.transfer-detected}") String fraudDetectedTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.signatureService = signatureService;
        this.transferApprovedTopic = transferApprovedTopic;
        this.fraudDetectedTopic = fraudDetectedTopic;
    }

    private Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
              .setSeconds(instant.getEpochSecond())
              .setNanos(instant.getNano())
              .build();
    }

    /**
     * Signature payload matches the contract used by all other services:
     * eventId + transactionId + epochSeconds (no delimiter — consistent with common-security).
     */
    private String buildSignature(String eventId,
                                  String transactionId,
                                  long epochSeconds) {
        return signatureService.sign(
              eventId,
              transactionId,
              String.valueOf(epochSeconds)
        );
    }

    // ── Internals ────────────────────────────────────────────────────────────
    private void sendSync(String topic,
                          String key,
                          Message message) {
        try {
            kafkaTemplate.send(topic, key, message).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KafkaPublishException(KAFKA_PUBLISH_INTERRUPTED, topic);
        } catch (ExecutionException e) {
            throw new KafkaPublishException(KAFKA_PUBLISH_FAILURE, topic);
        }catch (TimeoutException e) {
            throw new KafkaPublishException(KAFKA_PUBLISH_TIMEOUT, topic);
        }
    }

    @Override
    public void publishFraudDetected(String transactionId,
                                     String senderId,
                                     String reason) {
        String eventId = UUID.randomUUID().toString();
        Timestamp occurredAt = toTimestamp(Instant.now());

        String signature = buildSignature(
              eventId,
              transactionId,
              occurredAt.getSeconds()
        );

        FraudDetected event = FraudDetected.newBuilder()
              .setEventId(eventId)
              .setTransactionId(transactionId)
              .setSenderId(senderId)
              .setReason(reason)
              .setSignature(signature)
              .setOccurredAt(occurredAt)
              .build();

        sendSync(
              fraudDetectedTopic,
              transactionId,
              event
        );

        log.debug("FraudDetected sent: transaction={}", transactionId);
    }

    @Override
    public void publishTransferApproved(String transactionId) {
        String eventId = UUID.randomUUID().toString();
        Timestamp occurredAt = toTimestamp(Instant.now());

        String signature = buildSignature(
              eventId,
              transactionId,
              occurredAt.getSeconds()
        );

        TransferApproved event = TransferApproved.newBuilder()
                .setEventId(eventId)
                .setTransactionId(transactionId)
                .setSignature(signature)
                .setOccurredAt(occurredAt)
                .build();

        sendSync(
              transferApprovedTopic,
              transactionId,
              event
        );

        log.debug("TransferApproved sent: transaction={}", transactionId);
    }
}