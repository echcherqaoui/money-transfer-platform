package com.moneytransfer.transaction.consumer;

import com.moneytransfer.contract.TransferFailed;
import com.moneytransfer.exception.core.EventSecurityException;
import com.moneytransfer.security.service.ISignatureService;
import com.moneytransfer.transaction.service.ITransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static com.moneytransfer.transaction.enums.TransactionStatus.FAILED;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransferFailedConsumer {

    private final ITransactionService transactionService;
    private final ISignatureService signatureService;

    private void verifySignatureOrFail(TransferFailed event) {
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
    }

    @KafkaListener(
          topics = "${kafka.topics.wallet.transfer-failed}",
          groupId = "${spring.kafka.consumer.group-id}",
          containerFactory = "transferFailedListenerFactory"
    )
    public void consume(TransferFailed event, Acknowledgment ack) {
        log.info("Received TransferFailed for transaction: {} - reason: {}", 
                  event.getTransactionId(), event.getReason());

        // Verify HMAC signature — reject forged events
        verifySignatureOrFail(event);

        transactionService.updateStatus(
              UUID.fromString(event.getTransactionId()),
              UUID.fromString(event.getSenderId()),
              null,
              event.getReason(),
              FAILED
        );

        ack.acknowledge();
    }
}