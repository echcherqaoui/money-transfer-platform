package com.moneytransfer.transaction.repository;

import com.moneytransfer.transaction.enums.TransactionStatus;
import com.moneytransfer.transaction.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    // Used by timeout compensation job — finds stuck PENDING transactions
    @Query("""
            SELECT t FROM Transaction t
                WHERE t.status = 'PENDING'
                AND t.createdAt < :threshold
           """)
    List<Transaction> findStuckPendingTransactions(@Param("threshold") OffsetDateTime threshold);

    // Bulk status update
    @Modifying
    @Query("""
            UPDATE Transaction t SET t.status = :newStatus
                WHERE t.status = 'PENDING'
                AND t.createdAt < :threshold
           """)
    int markStuckTransactionsAs(@Param("newStatus") TransactionStatus newStatus,
                                @Param("threshold") OffsetDateTime threshold);
}