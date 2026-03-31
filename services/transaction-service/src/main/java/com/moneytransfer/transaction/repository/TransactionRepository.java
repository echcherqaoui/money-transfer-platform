package com.moneytransfer.transaction.repository;

import com.moneytransfer.transaction.enums.TransactionStatus;
import com.moneytransfer.transaction.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    // Bulk status update
    @Modifying
    @Query("""
            UPDATE Transaction t SET t.status = :newStatus, t.updatedAt = CURRENT_TIMESTAMP
                WHERE t.status = 'PENDING'
                AND t.createdAt < :threshold
           """)
    int markStuckTransactionsAs(@Param("newStatus") TransactionStatus newStatus,
                                @Param("threshold") OffsetDateTime threshold);

    @Transactional
    @Modifying
    @Query("""
            UPDATE Transaction t SET t.status = :newStatus, t.updatedAt = CURRENT_TIMESTAMP
                WHERE t.id = :transactionId
                AND t.status = :expectedStatus
           """)
    int atomicStatusUpdate(@Param("newStatus") TransactionStatus newStatus,
                           @Param("transactionId") UUID transactionId,
                           @Param("expectedStatus") TransactionStatus expectedStatus);
}