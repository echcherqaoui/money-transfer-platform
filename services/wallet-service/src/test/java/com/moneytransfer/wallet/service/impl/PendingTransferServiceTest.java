package com.moneytransfer.wallet.service.impl;

import com.moneytransfer.wallet.exception.WalletException;
import com.moneytransfer.wallet.model.PendingTransfer;
import com.moneytransfer.wallet.repository.PendingTransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static com.moneytransfer.wallet.enums.PendingStatus.COMPLETED;
import static com.moneytransfer.wallet.enums.PendingStatus.DISCARDED;
import static com.moneytransfer.wallet.enums.PendingStatus.INITIATED;
import static com.moneytransfer.wallet.exception.enums.WalletErrorCode.PENDING_TRANSFER_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PendingTransferService Unit Tests")
class PendingTransferServiceTest {
    @Mock
    private PendingTransferRepository pendingTransferRepository;
    @InjectMocks
    private PendingTransferService pendingTransferService;

    private UUID transactionId;
    private UUID senderId;
    private UUID receiverId;
    private BigDecimal amount;

    @BeforeEach
    void setUp() {
        transactionId = UUID.randomUUID();
        senderId = UUID.randomUUID();
        receiverId = UUID.randomUUID();
        amount = new BigDecimal("100.0000");
    }

    @Nested
    @DisplayName("storeAs Method")
    class StoreAs {
        @Test
        @DisplayName("Should store pending transfer with INITIATED status")
        void storeAs_Initiated() {
            // Given
            when(pendingTransferRepository.save(any(PendingTransfer.class)))
                  .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            pendingTransferService.storeAs(
                  transactionId,
                  senderId,
                  receiverId,
                  amount,
                  INITIATED
            );

            // Then
            ArgumentCaptor<PendingTransfer> captor = ArgumentCaptor.forClass(PendingTransfer.class);
            verify(pendingTransferRepository).save(captor.capture());

            PendingTransfer saved = captor.getValue();
            assertThat(saved.getTransactionId()).isEqualTo(transactionId);
            assertThat(saved.getSenderId()).isEqualTo(senderId);
            assertThat(saved.getReceiverId()).isEqualTo(receiverId);
            assertThat(saved.getAmount()).isEqualByComparingTo(amount);
            assertThat(saved.getStatus()).isEqualTo(INITIATED);
        }

        @Test
        @DisplayName("Should store pending transfer with DISCARDED status")
        void storeAs_Discarded() {
            // When
            pendingTransferService.storeAs(transactionId, senderId, receiverId, amount, DISCARDED);

            // Then
            ArgumentCaptor<PendingTransfer> captor = ArgumentCaptor.forClass(PendingTransfer.class);
            verify(pendingTransferRepository).save(captor.capture());

            assertThat(captor.getValue().getStatus()).isEqualTo(DISCARDED);
        }
    }

    @Nested
    @DisplayName("getPendingTransfer Method")
    class GetPendingTransfer {
        @Test
        @DisplayName("Should get pending transfer successfully")
        void getPendingTransfer_Success() {
            PendingTransfer pending = new PendingTransfer()
                  .setTransactionId(transactionId)
                  .setStatus(INITIATED);

            when(pendingTransferRepository.findById(transactionId))
                  .thenReturn(Optional.of(pending));

            // When
            PendingTransfer result = pendingTransferService.getPendingTransfer(transactionId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getTransactionId()).isEqualTo(transactionId);
            verify(pendingTransferRepository).findById(transactionId);
        }

        @Test
        @DisplayName("Should throw exception when pending transfer not found")
        void getPendingTransfer_NotFound() {
            // Given
            when(pendingTransferRepository.findById(transactionId))
                  .thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> pendingTransferService.getPendingTransfer(transactionId))
                  .isInstanceOf(WalletException.class)
                  .hasFieldOrPropertyWithValue("errorCode", PENDING_TRANSFER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("updateStatus Method")
    class UpdateStatus {
        @Test
        @DisplayName("Should update pending transfer status")
        void updateStatus_Success() {
            // Given
            PendingTransfer pending = new PendingTransfer()
                  .setTransactionId(transactionId)
                  .setStatus(INITIATED);

            when(pendingTransferRepository.save(any(PendingTransfer.class)))
                  .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            pendingTransferService.updateStatus(pending, COMPLETED);

            // Then
            ArgumentCaptor<PendingTransfer> captor = ArgumentCaptor.forClass(PendingTransfer.class);
            verify(pendingTransferRepository).save(captor.capture());

            assertThat(captor.getValue().getStatus()).isEqualTo(COMPLETED);
        }
    }

    @Nested
    @DisplayName("atomicStatusUpdate Method")
    class AtomicStatusUpdate {

        @Test
        @DisplayName("Should perform atomic status update successfully")
        void atomicStatusUpdate_Success() {
            // Given
            when(pendingTransferRepository.atomicStatusUpdate(DISCARDED, transactionId, INITIATED))
                  .thenReturn(1);

            // When
            int rowsUpdated = pendingTransferService.atomicStatusUpdate(transactionId, DISCARDED);

            // Then
            assertThat(rowsUpdated).isEqualTo(1);
            verify(pendingTransferRepository).atomicStatusUpdate(DISCARDED, transactionId, INITIATED);
        }

        @Test
        @DisplayName("Should return 0 when atomic update finds no matching row")
        void atomicStatusUpdate_NoMatch() {
            // Given
            when(pendingTransferRepository.atomicStatusUpdate(DISCARDED, transactionId, INITIATED))
                  .thenReturn(0);

            // When
            int rowsUpdated = pendingTransferService.atomicStatusUpdate(transactionId, DISCARDED);

            // Then
            assertThat(rowsUpdated).isZero();
        }
    }
}