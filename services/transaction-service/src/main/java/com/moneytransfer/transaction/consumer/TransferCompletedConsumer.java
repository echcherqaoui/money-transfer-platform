package com.moneytransfer.transaction.consumer;

import com.moneytransfer.contract.TransferCompleted;
import com.moneytransfer.exception.core.EventSecurityException;
import com.moneytransfer.security.service.ISignatureService;
import com.moneytransfer.transaction.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static com.moneytransfer.transaction.enums.TransactionStatus.COMPLETED;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransferCompletedConsumer {

    private final TransactionService transactionService;
    private final ISignatureService signatureService;

/*    @KafkaListener(
            topics = "${topic}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "transferCompletedListenerFactory"
    )*/
    public void consume(TransferCompleted event, Acknowledgment ack) {
        log.info("Received TransferCompleted for transaction: {}", event.getTransactionId());

        // Verify HMAC signature — reject forged events
        boolean valid = signatureService.verify(
                event.getEventId(),
                event.getTransactionId(),
                String.valueOf(event.getOccurredAt().getSeconds()),
                event.getSignature()
        );

        if (!valid) {
            log.error("Invalid signature on TransferCompleted event: {} — discarding", event.getEventId());
            throw new EventSecurityException();
        }

        // Update transaction status to COMPLETED
        transactionService.updateStatus(
                UUID.fromString(event.getTransactionId()),
                COMPLETED
        );

        ack.acknowledge();
        log.info("Transaction {} marked COMPLETED", event.getTransactionId());
    }
}