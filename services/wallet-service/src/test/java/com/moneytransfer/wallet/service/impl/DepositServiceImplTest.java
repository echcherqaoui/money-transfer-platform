package com.moneytransfer.wallet.service.impl;

import com.moneytransfer.wallet.dto.WalletResponse;
import com.moneytransfer.wallet.model.DepositLog;
import com.moneytransfer.wallet.model.Wallet;
import com.moneytransfer.wallet.repository.DepositLogRepository;
import com.moneytransfer.wallet.service.IDepositLimitService;
import com.moneytransfer.wallet.service.ISseEmitterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DepositServiceImpl Unit Tests")
class DepositServiceImplTest {
    @Mock
    private DepositLogRepository depositLogRepository;
    @Mock
    private WalletService walletService;
    @Mock
    private IDepositLimitService limitService;
    @Mock
    private ISseEmitterService sseEmitterService;
    @InjectMocks
    private DepositServiceImpl depositService;

    private UUID userId;
    private BigDecimal depositAmount;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        depositAmount = new BigDecimal("100.0000");

        wallet = new Wallet()
              .setId(UUID.randomUUID())
              .setUserId(userId)
              .setBalance(new BigDecimal("200.0000"));
    }

    @Test
    @DisplayName("Should deposit successfully - happy path")
    void deposit_Success() {
        // Given
        doNothing().when(limitService).validate(userId, depositAmount);

        when(walletService.credit(userId, depositAmount))
              .thenReturn(wallet);

        when(depositLogRepository.save(any(DepositLog.class)))
              .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        WalletResponse response = depositService.deposit(userId, depositAmount);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.balance()).isEqualTo(new BigDecimal("200.0000"));

        verify(limitService).validate(userId, depositAmount);
        verify(walletService).credit(userId, depositAmount);

        ArgumentCaptor<DepositLog> captor = ArgumentCaptor.forClass(DepositLog.class);
        verify(depositLogRepository).save(captor.capture());

        DepositLog savedLog = captor.getValue();
        assertThat(savedLog.getUserId()).isEqualTo(userId);
        assertThat(savedLog.getAmount()).isEqualByComparingTo(depositAmount);

        verify(sseEmitterService).pushBalanceUpdate(userId, wallet.getBalance());
    }

    @Test
    @DisplayName("Should validate limits before processing")
    void deposit_ValidatesLimits() {
        // Given
        doNothing().when(limitService).validate(userId, depositAmount);
        when(walletService.credit(userId, depositAmount))
              .thenReturn(wallet);

        // When
        depositService.deposit(userId, depositAmount);

        // Then
        verify(limitService).validate(userId, depositAmount);
    }

    @Test
    @DisplayName("Should save deposit log entry")
    void deposit_SavesLog() {
        // Given
        doNothing().when(limitService).validate(userId, depositAmount);
        when(walletService.credit(userId, depositAmount))
              .thenReturn(wallet);

        // When
        depositService.deposit(userId, depositAmount);

        // Then
        ArgumentCaptor<DepositLog> captor = ArgumentCaptor.forClass(DepositLog.class);
        verify(depositLogRepository).save(captor.capture());

        DepositLog log = captor.getValue();
        assertThat(log.getUserId()).isEqualTo(userId);
        assertThat(log.getAmount()).isEqualByComparingTo(depositAmount);
    }

    @Test
    @DisplayName("Should push SSE update after deposit")
    void deposit_PushesSseUpdate() {
        // Given
        doNothing().when(limitService).validate(userId, depositAmount);
        when(walletService.credit(userId, depositAmount)).thenReturn(wallet);

        // When
        depositService.deposit(userId, depositAmount);

        // Then
        verify(sseEmitterService).pushBalanceUpdate(userId, wallet.getBalance());
    }
}