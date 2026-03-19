package com.moneytransfer.transaction.scheduler;

import com.moneytransfer.transaction.enums.TransactionStatus;
import com.moneytransfer.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class TimeoutCompensationJob {

    private final TransactionRepository transactionRepository;

    @Value("${transaction.timeout.minutes:10}")
    private int timeoutMinutes;

    // Runs every 1 minute — checks for PENDING transactions older than timeoutMinutes
    @Scheduled(fixedDelayString = "PT1M")
    @Transactional
    public void markStuckTransactionsFailed() {
        OffsetDateTime threshold = OffsetDateTime.now().minusMinutes(timeoutMinutes);

        int updated = transactionRepository.markStuckTransactionsAs(
                TransactionStatus.FAILED,
                threshold
        );

        if (updated > 0)
            log.warn(
                  "Timeout compensation: marked {} stuck PENDING transaction(s) as FAILED (older than {} minutes)",
                  updated,
                  timeoutMinutes
            );
        else
            log.debug("Timeout compensation: no stuck transactions found");
    }
}