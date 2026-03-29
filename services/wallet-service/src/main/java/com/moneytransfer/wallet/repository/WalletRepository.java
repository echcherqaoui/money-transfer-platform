package com.moneytransfer.wallet.repository;


import com.moneytransfer.wallet.model.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);


    /**
     * Pessimistic write lock — prevents concurrent balance updates
     * for the same wallet within the same transfer settlement.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.userId = :userId")
    Optional<Wallet> findByUserIdForUpdate(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT w FROM Wallet w
            WHERE w.userId IN :userIds ORDER BY w.userId
           """)
    List<Wallet> findAllByUserIdInForUpdate(@Param("userIds") List<UUID> userIds);
}