package com.moneytransfer.wallet.consumer;

import com.moneytransfer.contract.TransferApproved;
import com.moneytransfer.exception.core.EventSecurityException;
import com.moneytransfer.security.service.ISignatureService;
import com.moneytransfer.wallet.service.ITransferOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransferApprovedConsumer {
    private final ISignatureService signatureService;
    private final ITransferOrchestrator transferOrchestrator;

    private void verifySignatureOrFail(TransferApproved event) {
        boolean valid = signatureService.verify(
              event.getEventId(),
              event.getTransactionId(),
              String.valueOf(event.getOccurredAt().getSeconds()),
              event.getSignature()
        );
        if (!valid) {
            log.error("Invalid HMAC signature on TransferApproved: eventId={}", event.getEventId());
            throw new EventSecurityException();
        }
    }

    @KafkaListener(
          topics = "${kafka.topics.fraud.transfer-approved}",
          groupId = "${spring.kafka.consumer.group-id}",
          containerFactory = "transferApprovedListenerContainerFactory"
    )
    public void consume(TransferApproved event, Acknowledgment ack) {
        log.debug("Received TransferApproved: transaction={}", event.getTransactionId());

        verifySignatureOrFail(event);

        transferOrchestrator.handleApproved(event);

        ack.acknowledge();
    }
}