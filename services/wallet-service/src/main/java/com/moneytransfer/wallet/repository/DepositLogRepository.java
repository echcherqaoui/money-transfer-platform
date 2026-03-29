package com.moneytransfer.wallet.repository;

import com.moneytransfer.wallet.model.DepositLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public interface DepositLogRepository extends JpaRepository<DepositLog, UUID> {
    @Query("""
            SELECT COALESCE(SUM(d.amount), 0) FROM DepositLog d
                WHERE d.userId = :userId AND d.depositedAt >= :startOfDay
           """)
    BigDecimal sumDepositedToday(@Param("userId") UUID userId,
                                 @Param("startOfDay") OffsetDateTime startOfDay);
}