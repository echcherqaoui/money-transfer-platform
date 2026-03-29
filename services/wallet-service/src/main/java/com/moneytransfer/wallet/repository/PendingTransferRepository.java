package com.moneytransfer.wallet.repository;

import com.moneytransfer.wallet.enums.PendingStatus;
import com.moneytransfer.wallet.model.PendingTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public interface PendingTransferRepository extends JpaRepository<PendingTransfer, UUID> {

    @Transactional
    @Modifying
    @Query("""
            UPDATE PendingTransfer p
            SET p.status = :newStatus, p.updatedAt = CURRENT_TIMESTAMP
            WHERE p.transactionId = :transactionId
            AND p.status = :expectedStatus
           """)
    int atomicStatusUpdate(
          @Param("newStatus") PendingStatus newStatus,
          @Param("transactionId") UUID transactionId,
          @Param("expectedStatus") PendingStatus expectedStatus
    );
}