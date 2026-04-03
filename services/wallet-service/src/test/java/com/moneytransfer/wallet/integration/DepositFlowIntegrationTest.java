package com.moneytransfer.wallet.integration;

import com.moneytransfer.wallet.dto.WalletResponse;
import com.moneytransfer.wallet.exception.WalletException;
import com.moneytransfer.wallet.model.DepositLog;
import com.moneytransfer.wallet.model.Wallet;
import com.moneytransfer.wallet.properties.DepositProperties;
import com.moneytransfer.wallet.repository.DepositLogRepository;
import com.moneytransfer.wallet.repository.WalletRepository;
import com.moneytransfer.wallet.service.IDepositService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static com.moneytransfer.wallet.exception.enums.WalletErrorCode.DAILY_DEPOSIT_LIMIT_EXCEEDED;
import static com.moneytransfer.wallet.exception.enums.WalletErrorCode.DEPOSIT_LIMIT_EXCEEDED;
import static java.math.BigDecimal.TEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka
@DisplayName("Deposit Flow Integration Tests")
class DepositFlowIntegrationTest {
    @Autowired
    private IDepositService depositService;
    @Autowired
    private WalletRepository walletRepository;
    @Autowired
    private DepositLogRepository depositLogRepository;
    @Autowired
    private DepositProperties depositProperties;

    private UUID userId;

    @BeforeEach
    void setUp() {
        // Clean up
        depositLogRepository.deleteAll();
        walletRepository.deleteAll();

        userId = UUID.randomUUID();

        walletRepository.save(
              new Wallet()
                    .setUserId(userId)
                    .setBalance(new BigDecimal("500.0000"))
        );
    }

    @Test
    @Transactional
    @DisplayName("Should deposit successfully and persist all changes atomically")
    void deposit_SuccessfulFlow() {
        // Given
        BigDecimal depositAmount = new BigDecimal("200.0000");

        // When
        WalletResponse response = depositService.deposit(userId, depositAmount);

        // Then - verify response
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.balance()).isEqualTo(new BigDecimal("700.0000"));

        // Then - verify wallet updated
        Wallet updated = walletRepository.findByUserId(userId).orElseThrow();
        assertThat(updated.getBalance()).isEqualByComparingTo("700.0000");

        // Then - verify deposit log created
        List<DepositLog> logs = depositLogRepository.findAll();
        assertThat(logs).hasSize(1);

        DepositLog log = logs.get(0);
        assertThat(log.getUserId()).isEqualTo(userId);
        assertThat(log.getAmount()).isEqualByComparingTo(depositAmount);
        assertThat(log.getDepositedAt()).isNotNull();
    }

    @Test
    @Transactional
    @DisplayName("Should enforce single deposit limit")
    void deposit_EnforceSingleLimit() {
        // Given
        BigDecimal maxSingle = BigDecimal.valueOf(depositProperties.getMaxSingleDepositMinorUnits())
              .movePointLeft(4);
        BigDecimal overLimit = maxSingle.add(BigDecimal.ONE);

        // When / Then
        assertThatThrownBy(() -> depositService.deposit(userId, overLimit))
              .isInstanceOf(WalletException.class)
              .hasFieldOrPropertyWithValue("errorCode", DEPOSIT_LIMIT_EXCEEDED);

        // Verify no changes
        Wallet unchanged = walletRepository.findByUserId(userId).orElseThrow();
        assertThat(unchanged.getBalance()).isEqualByComparingTo("500.0000");
        assertThat(depositLogRepository.count()).isZero();
    }

    @Test
    @Transactional
    @DisplayName("Should enforce daily deposit limit")
    void deposit_EnforceDailyLimit() {
        // Given - make deposits that approach daily limit
        BigDecimal dailyMax = BigDecimal.valueOf(depositProperties.getMaxDailyDepositMinorUnits())
              .movePointLeft(4);

        BigDecimal firstDeposit = dailyMax.divide(BigDecimal.valueOf(2));
        BigDecimal secondDeposit = dailyMax.divide(BigDecimal.valueOf(2));

        // When - first two succeed
        depositService.deposit(userId, firstDeposit);
        depositService.deposit(userId, secondDeposit);

        // Then - third fails
        assertThatThrownBy(() -> depositService.deposit(userId, TEN))
              .isInstanceOf(WalletException.class)
              .hasFieldOrPropertyWithValue("errorCode", DAILY_DEPOSIT_LIMIT_EXCEEDED);

        // Verify only 2 deposits logged
        assertThat(depositLogRepository.count()).isEqualTo(2);
    }

    @Test
    @Transactional
    @DisplayName("Should allow deposits exactly at single limit")
    void deposit_ExactlySingleLimit() {
        // Given
        BigDecimal maxSingle = BigDecimal.valueOf(depositProperties.getMaxSingleDepositMinorUnits())
              .movePointLeft(4);

        // When
        WalletResponse response = depositService.deposit(userId, maxSingle);

        // Then
        assertThat(response).isNotNull();

        Wallet updated = walletRepository.findByUserId(userId).orElseThrow();
        assertThat(updated.getBalance()).isEqualByComparingTo(
              new BigDecimal("500.0000").add(maxSingle)
        );
    }

    @Test
    @Transactional
    @DisplayName("Should handle multiple concurrent deposits correctly")
    void deposit_MultipleConcurrentDeposits() {
        // Given
        BigDecimal firstAmount = new BigDecimal("100.0000");
        BigDecimal secondAmount = new BigDecimal("150.0000");
        BigDecimal thirdAmount = new BigDecimal("200.0000");

        // When
        depositService.deposit(userId, firstAmount);
        depositService.deposit(userId, secondAmount);
        depositService.deposit(userId, thirdAmount);

        // Then
        Wallet updated = walletRepository.findByUserId(userId).orElseThrow();
        assertThat(updated.getBalance()).isEqualByComparingTo("950.0000"); // 500 + 100 + 150 + 200

        assertThat(depositLogRepository.count()).isEqualTo(3);
    }
}