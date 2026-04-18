package com.moneytransfer.transaction.repository;

import com.moneytransfer.transaction.dto.response.TransactionStatsResponse;
import com.moneytransfer.transaction.enums.TransactionStatus;
import com.moneytransfer.transaction.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    @Query("""
          SELECT t FROM Transaction t
              WHERE t.id = :id
                  AND (
                      t.senderId = :userId
                      OR (t.receiverId = :userId AND t.status = 'COMPLETED')
                  )
          """)
    Optional<Transaction> findAccessibleTransaction(@Param("id") UUID id, @Param("userId") UUID userId);

    /**
     * Bulk status update
     */
    @Modifying(clearAutomatically = true)
    @Query("""
              UPDATE Transaction t
                  SET t.status = :newStatus,
                      t.failureReason = :failureReason,
                      t.updatedAt = CURRENT_TIMESTAMP
                  WHERE t.status = 'PENDING'
                      AND t.createdAt < :threshold
          """)
    int markStuckTransactionsAs(@Param("newStatus") TransactionStatus newStatus,
                                @Param("failureReason") String failureReason,
                                @Param("threshold") OffsetDateTime threshold);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
              UPDATE Transaction t
                  SET t.status = :newStatus,
                      t.failureReason = :failureReason,
                      t.updatedAt = CURRENT_TIMESTAMP
                  WHERE t.id = :transactionId
                      AND t.status = :expectedStatus
          """)
    int atomicStatusUpdate(@Param("newStatus") TransactionStatus newStatus,
                           @Param("transactionId") UUID transactionId,
                           @Param("failureReason") String failureReason,
                           @Param("expectedStatus") TransactionStatus expectedStatus);

    /**
     * Find transactions where user is sender or receiver
     * - As sender: sees ALL statuses
     * - As receiver: sees only COMPLETED
     */
    @Query("""
              SELECT t FROM Transaction t
                  WHERE t.senderId = :userId
                      OR (t.receiverId = :userId AND t.status = 'COMPLETED')
          """)
    Page<Transaction> findByUserInvolvement(@Param("userId") UUID userId, Pageable pageable);

    /**
     * Only COMPLETED transactions (both as sender and receiver)
     */
    @Query("""
              SELECT t FROM Transaction t
                  WHERE (t.senderId = :userId OR t.receiverId = :userId)
                      AND t.status = 'COMPLETED'
          """)
    Page<Transaction> findCompletedByUserInvolvement(@Param("userId") UUID userId, Pageable pageable);

    /**
     * Filter by specific status (For sender side)
     */
    @Query("""
              SELECT t FROM Transaction t
                  WHERE t.senderId = :userId AND t.status = :status
          """)
    Page<Transaction> findBySenderAndStatus(@Param("userId") UUID userId,
                                            @Param("status") TransactionStatus status,
                                            Pageable pageable);

    /**
     * Calculate total sent by user
     * Calculate total received by user
     * Count all transactions involving user
     */
    @Query("""
              SELECT
                  COALESCE(SUM(CASE WHEN t.senderId = :userId THEN t.amount ELSE 0 END), 0),
                  COALESCE(SUM(CASE WHEN t.receiverId = :userId THEN t.amount ELSE 0 END), 0),
                  COUNT(t.id)
              FROM Transaction t
                  WHERE (t.senderId = :userId OR t.receiverId = :userId) AND t.status = :status
          """)
    TransactionStatsResponse getStatsByUserId(@Param("userId") UUID userId,
                                              @Param("status") TransactionStatus status);
}