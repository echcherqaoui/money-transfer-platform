package com.moneytransfer.wallet.idempotency.impl;

import com.moneytransfer.wallet.idempotency.IIdempotencyGuard;
import com.moneytransfer.wallet.model.ProcessedEvent;
import com.moneytransfer.wallet.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DbIdempotencyGuard implements IIdempotencyGuard {
    
    private final ProcessedEventRepository processedEventRepository;
    
    /**
     * @return true if already processed (skip), false if newly recorded (proceed)
     */
    @Override
    @Transactional
    public boolean isProcessed(String eventId) {
        try {
            processedEventRepository.save(
                  new ProcessedEvent()
                        .setEventId(eventId)
            );
            return false;
        } catch (DataIntegrityViolationException e) {
            return true;
        }
    }
}