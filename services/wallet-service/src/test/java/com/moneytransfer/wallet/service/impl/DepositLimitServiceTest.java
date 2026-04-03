package com.moneytransfer.wallet.service.impl;

import com.moneytransfer.wallet.exception.WalletException;
import com.moneytransfer.wallet.properties.DepositProperties;
import com.moneytransfer.wallet.repository.DepositLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static com.moneytransfer.wallet.exception.enums.WalletErrorCode.DAILY_DEPOSIT_LIMIT_EXCEEDED;
import static com.moneytransfer.wallet.exception.enums.WalletErrorCode.DEPOSIT_LIMIT_EXCEEDED;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DepositLimitService Unit Tests")
class DepositLimitServiceTest {
    @Mock
    private DepositLogRepository depositLogRepository;
    @Mock
    private DepositProperties depositProperties;
    @InjectMocks
    private DepositLimitService depositLimitService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        // Default limits
        lenient().when(depositProperties.getMaxSingleDepositMinorUnits()).thenReturn(10_000_000L);
        lenient().when(depositProperties.getMaxDailyDepositMinorUnits()).thenReturn(50_000_000L);
    }

    @Test
    @DisplayName("Should pass validation when within limits")
    void validate_WithinLimits() {
        // Given
        BigDecimal amount = new BigDecimal("500.0000");
        when(depositLogRepository.sumDepositedToday(eq(userId), any(OffsetDateTime.class)))
              .thenReturn(new BigDecimal("1000.0000"));

        // When / Then
        assertThatCode(() -> depositLimitService.validate(userId, amount))
              .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should throw exception when single deposit exceeds limit")
    void validate_SingleLimitExceeded() {
        // Given
        BigDecimal amount = new BigDecimal("1500.0000"); // Over 1000.00 limit

        // When / Then
        assertThatThrownBy(() -> depositLimitService.validate(userId, amount))
              .isInstanceOf(WalletException.class)
              .hasFieldOrPropertyWithValue("errorCode", DEPOSIT_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("Should throw exception when daily limit would be exceeded")
    void validate_DailyLimitExceeded() {
        // Given
        BigDecimal amount = new BigDecimal("1000.0000");
        when(depositLogRepository.sumDepositedToday(eq(userId), any(OffsetDateTime.class)))
              .thenReturn(new BigDecimal("4500.0000")); // Already deposited 4500, +1000 = 5500 > 5000

        // When / Then
        assertThatThrownBy(() -> depositLimitService.validate(userId, amount))
              .isInstanceOf(WalletException.class)
              .hasFieldOrPropertyWithValue("errorCode", DAILY_DEPOSIT_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("Should allow deposit exactly at single limit")
    void validate_ExactlySingleLimit() {
        // Given
        BigDecimal amount = new BigDecimal("1000.0000"); // Exactly at limit
        when(depositLogRepository.sumDepositedToday(eq(userId), any(OffsetDateTime.class)))
              .thenReturn(BigDecimal.ZERO);

        // When / Then
        assertThatCode(() -> depositLimitService.validate(userId, amount))
              .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should allow deposit exactly at daily limit")
    void validate_ExactlyDailyLimit() {
        // Given
        BigDecimal amount = new BigDecimal("1000.0000");
        when(depositLogRepository.sumDepositedToday(eq(userId), any(OffsetDateTime.class)))
              .thenReturn(new BigDecimal("4000.0000")); // 4000 + 1000 = exactly 5000

        // When / Then
        assertThatCode(() -> depositLimitService.validate(userId, amount))
              .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should reject when just over single limit")
    void validate_JustOverSingleLimit() {
        // Given
        BigDecimal amount = new BigDecimal("1000.0001");

        // When / Then
        assertThatThrownBy(() -> depositLimitService.validate(userId, amount))
              .isInstanceOf(WalletException.class)
              .hasFieldOrPropertyWithValue("errorCode", DEPOSIT_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("Should reject when just over daily limit")
    void validate_JustOverDailyLimit() {
        // Given
        BigDecimal amount = new BigDecimal("1000.0000");
        when(depositLogRepository.sumDepositedToday(eq(userId), any(OffsetDateTime.class)))
              .thenReturn(new BigDecimal("4000.0001")); // 4000.0001 + 1000 > 5000

        // When / Then
        assertThatThrownBy(() -> depositLimitService.validate(userId, amount))
              .isInstanceOf(WalletException.class)
              .hasFieldOrPropertyWithValue("errorCode", DAILY_DEPOSIT_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("Should handle zero prior deposits")
    void validate_ZeroPriorDeposits() {
        // Given
        BigDecimal amount = new BigDecimal("500.0000");
        when(depositLogRepository.sumDepositedToday(eq(userId), any(OffsetDateTime.class)))
              .thenReturn(BigDecimal.ZERO);

        // When / Then
        assertThatCode(() -> depositLimitService.validate(userId, amount))
              .doesNotThrowAnyException();
    }
}