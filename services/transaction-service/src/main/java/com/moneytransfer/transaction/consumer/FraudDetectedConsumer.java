package com.moneytransfer.transaction.consumer;

import com.moneytransfer.contract.FraudDetected;
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
public class FraudDetectedConsumer {

    private final ITransactionService transactionService;
    private final ISignatureService signatureService;

    private void verifySignatureOrFail(FraudDetected event) {
        boolean valid = signatureService.verify(
              event.getEventId(),
              event.getTransactionId(),
              String.valueOf(event.getOccurredAt().getSeconds()),
              event.getSignature()
        );

        if (!valid) {
            log.error("Invalid signature on FraudDetected event: {} — discarding", event.getEventId());
            throw new EventSecurityException();
        }
    }

    @KafkaListener(
          topics = "${kafka.topics.fraud.transfer-detected}",
          groupId = "${spring.kafka.consumer.group-id}",
          containerFactory = "fraudDetectedListenerFactory"
    )
    public void consume(FraudDetected event, Acknowledgment ack) {
        log.info(
              "Received FraudDetected for transaction: {} — reason: {}",
              event.getTransactionId(),
              event.getReason()
        );

        verifySignatureOrFail(event);

        // Mark transaction as FAILED
        transactionService.updateStatus(
              UUID.fromString(event.getTransactionId()),
              UUID.fromString(event.getSenderId()),
              null,
              event.getReason(),
              FAILED
        );

        ack.acknowledge();
        log.info(
              "Transaction {} marked FAILED due to fraud: {}",
              event.getTransactionId(),
              event.getReason()
        );
    }
}