package com.moneytransfer.transaction.service.impl;

import com.google.protobuf.Message;
import com.google.protobuf.util.Timestamps;
import com.moneytransfer.contract.MoneyTransferInitiated;
import com.moneytransfer.security.service.ISignatureService;
import com.moneytransfer.transaction.model.OutboxEvent;
import com.moneytransfer.transaction.model.Transaction;
import com.moneytransfer.transaction.properties.TransactionProperties;
import com.moneytransfer.transaction.repository.OutboxEventRepository;
import com.moneytransfer.transaction.service.ITransactionOutboxService;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static java.math.RoundingMode.HALF_UP;
import static java.time.temporal.ChronoUnit.MINUTES;

@Service
public class TransactionOutboxServiceImpl implements ITransactionOutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ISignatureService signatureService;
    private final KafkaProtobufSerializer<Message> serializer;
    private final String transferInitiatedTopic;
    private final TransactionProperties transactionProperties;

    private static final String AGGREGATE_TYPE = "Transaction";
    private static final String EVENT_TYPE_INITIATED = "initiated.v1";
    private static final Duration SAFETY_BUFFER = Duration.ofSeconds(30);


    public TransactionOutboxServiceImpl(OutboxEventRepository outboxEventRepository,
                                        ISignatureService signatureService,
                                        KafkaProtobufSerializer<Message> serializer,
                                        @Value("${kafka.topics.transaction.transfer-initiated}") String transferInitiatedTopic,
                                        TransactionProperties transactionProperties) {
        this.outboxEventRepository = outboxEventRepository;
        this.signatureService = signatureService;
        this.serializer = serializer;
        this.transferInitiatedTopic = transferInitiatedTopic;
        this.transactionProperties = transactionProperties;
    }

    @Override
    public void publishTransferInitiated(Transaction transaction) {
        String eventId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        String signature = signatureService.sign(
              eventId,
              transaction.getId().toString(),
              String.valueOf(now.getEpochSecond())
        );

        long amountCents = transaction.getAmount()
              .setScale(4, HALF_UP)
              .movePointRight(4)
              .longValueExact();

        Instant expiresAt = now.plus(transactionProperties.getTimeoutMinutes(), MINUTES)
              .minus(SAFETY_BUFFER);

        MoneyTransferInitiated proto = MoneyTransferInitiated.newBuilder()
              .setEventId(eventId)
              .setSignature(signature)
              .setOccurredAt(Timestamps.fromMillis(now.toEpochMilli()))
              .setTransactionId(transaction.getId().toString())
              .setSenderId(transaction.getSenderId().toString())
              .setReceiverId(transaction.getReceiverId().toString())
              .setAmountMinorUnits(amountCents)
              .setExpiresAt(Timestamps.fromMillis(expiresAt.toEpochMilli()))
              .build();

        byte[] serialized = serializer.serialize(transferInitiatedTopic, proto);

        OutboxEvent outboxEvent = new OutboxEvent()
              .setAggregateType(AGGREGATE_TYPE)
              .setAggregateId(transaction.getId())
              .setEventType(EVENT_TYPE_INITIATED)
              .setPayload(serialized);

        outboxEventRepository.save(outboxEvent);
    }
}