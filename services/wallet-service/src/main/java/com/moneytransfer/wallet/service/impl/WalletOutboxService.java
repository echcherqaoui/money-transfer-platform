package com.moneytransfer.wallet.service.impl;

import com.google.protobuf.Message;
import com.google.protobuf.util.Timestamps;
import com.moneytransfer.contract.TransferCompleted;
import com.moneytransfer.contract.TransferFailed;
import com.moneytransfer.security.service.ISignatureService;
import com.moneytransfer.wallet.model.OutboxEvent;
import com.moneytransfer.wallet.repository.OutboxEventRepository;
import com.moneytransfer.wallet.service.IWalletOutboxService;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class WalletOutboxService implements IWalletOutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ISignatureService signatureService;
    private final KafkaProtobufSerializer<Message> serializer;
    private final String transferCompletedTopic;
    private final String transferFailedTopic;

    private static final String AGGREGATE_TYPE = "wallet";
    private static final String EVENT_TYPE_COMPLETED = "completed.v1";
    private static final String EVENT_TYPE_FAILED = "failed.v1";

    public WalletOutboxService(OutboxEventRepository outboxEventRepository,
                               ISignatureService signatureService,
                               KafkaProtobufSerializer<Message> serializer,
                               @Value("${kafka.topics.wallet.transfer-completed}") String transferCompletedTopic,
                               @Value("${kafka.topics.wallet.transfer-failed}") String transferFailedTopic) {
        this.outboxEventRepository = outboxEventRepository;
        this.signatureService = signatureService;
        this.serializer = serializer;
        this.transferCompletedTopic = transferCompletedTopic;
        this.transferFailedTopic = transferFailedTopic;
    }

    @Override
    public void publishTransferCompleted(UUID transactionId,
                                         UUID senderId,
                                         UUID receiverId,
                                         long amountMinorUnits,
                                         long senderNewBalance,
                                         long receiverNewBalance) {
        String eventId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        String signature = signatureService.sign(
              eventId,
              transactionId.toString(),
              String.valueOf(now.getEpochSecond())
        );

        TransferCompleted event = TransferCompleted.newBuilder()
              .setEventId(eventId)
              .setTransactionId(transactionId.toString())
              .setSenderId(senderId.toString())
              .setReceiverId(receiverId.toString())
              .setAmountMinorUnits(amountMinorUnits)
              .setSenderNewBalanceMinor(senderNewBalance)
              .setReceiverNewBalanceMinor(receiverNewBalance)
              .setSignature(signature)
              .setOccurredAt(Timestamps.fromMillis(now.toEpochMilli()))
              .build();

        byte[] serialized = serializer.serialize(transferCompletedTopic, event);

        OutboxEvent outboxEvent = new OutboxEvent()
              .setAggregateType(AGGREGATE_TYPE)
              .setAggregateId(transactionId)
              .setEventType(EVENT_TYPE_COMPLETED)
              .setPayload(serialized);

        outboxEventRepository.save(outboxEvent);
    }

    @Override
    public void publishTransferFailed(UUID transactionId,
                                      UUID senderId,
                                      String reason) {
        String eventId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        String signature = signatureService.sign(
              eventId,
              transactionId.toString(),
              String.valueOf(now.getEpochSecond())
        );

        TransferFailed event = TransferFailed.newBuilder()
              .setEventId(eventId)
              .setTransactionId(transactionId.toString())
              .setSenderId(senderId.toString())
              .setReason(reason)
              .setSignature(signature)
              .setOccurredAt(Timestamps.fromMillis(now.toEpochMilli()))
              .build();

        byte[] serialized = serializer.serialize(transferFailedTopic, event);

        OutboxEvent outboxEvent = new OutboxEvent()
              .setAggregateType(AGGREGATE_TYPE)
              .setAggregateId(transactionId)
              .setEventType(EVENT_TYPE_FAILED)
              .setPayload(serialized);

        outboxEventRepository.save(outboxEvent);
    }
}