package com.moneytransfer.transaction.service.impl;

import com.google.protobuf.Message;
import com.google.protobuf.util.Timestamps;
import com.moneytransfer.contract.MoneyTransferInitiated;
import com.moneytransfer.security.service.ISignatureService;
import com.moneytransfer.transaction.model.OutboxEvent;
import com.moneytransfer.transaction.model.Transaction;
import com.moneytransfer.transaction.repository.OutboxEventRepository;
import com.moneytransfer.transaction.service.ITransactionOutboxService;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Service
public class TransactionOutboxServiceImpl implements ITransactionOutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ISignatureService signatureService;
    private final KafkaProtobufSerializer<Message> serializer;
    private final String transferInitiatedTopic;

    private static final String AGGREGATE_TYPE = "Transaction";
    private static final String EVENT_TYPE_INITIATED = "initiated.v1";

    public TransactionOutboxServiceImpl(OutboxEventRepository outboxEventRepository,
                                        ISignatureService signatureService,
                                        KafkaProtobufSerializer<Message> serializer,
                                        @Value("${kafka.topics.transaction.transfer-initiated}") String transferInitiatedTopic) {
        this.outboxEventRepository = outboxEventRepository;
        this.signatureService = signatureService;
        this.serializer = serializer;
        this.transferInitiatedTopic = transferInitiatedTopic;
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
                .setScale(4, RoundingMode.HALF_UP)
                .movePointRight(4)
                .longValueExact();

        MoneyTransferInitiated proto = MoneyTransferInitiated.newBuilder()
                .setEventId(eventId)
                .setSignature(signature)
                .setOccurredAt(Timestamps.fromMillis(now.toEpochMilli()))
                .setTransactionId(transaction.getId().toString())
                .setSenderId(transaction.getSenderId().toString())
                .setReceiverId(transaction.getReceiverId().toString())
                .setAmountMinorUnits(amountCents)
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