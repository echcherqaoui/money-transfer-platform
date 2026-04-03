package com.moneytransfer.fraud.service.impl;

import com.google.protobuf.Timestamp;
import com.moneytransfer.contract.MoneyTransferInitiated;
import com.moneytransfer.fraud.properties.FraudProperties;
import com.moneytransfer.fraud.service.IFraudEventProducer;
import com.moneytransfer.fraud.velocity.IVelocityTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FraudDetectionServiceImpl Unit Tests")
class FraudDetectionServiceImplTest {

    @Mock
    private FraudProperties fraudProperties;
    @Mock
    private IVelocityTracker velocityTracker;
    @Mock
    private IFraudEventProducer fraudEventProducer;
    @InjectMocks
    private FraudDetectionServiceImpl fraudDetectionService;

    private FraudProperties.Velocity velocityConfig;

    @BeforeEach
    void setUp() {
        velocityConfig = new FraudProperties.Velocity();
        velocityConfig.setMaxTransactions(5);
        velocityConfig.setWindowMinutes(10);

        // Lenient prevents Mockito from failing when a test (like Expiry) exits before reaching these calls
        lenient().when(fraudProperties.getAmountThresholdMinorUnits()).thenReturn(100_000_000L);
        lenient().when(fraudProperties.getVelocity()).thenReturn(velocityConfig);
    }

    /**
     * Helper to create events with sensible defaults.
     * Overriding parameters allows targeting specific test scenarios.
     */
    private MoneyTransferInitiated createEvent(long amount, int expirySeconds) {
        return MoneyTransferInitiated.newBuilder()
              .setEventId(UUID.randomUUID().toString())
              .setTransactionId(UUID.randomUUID().toString())
              .setSenderId(UUID.randomUUID().toString())
              .setReceiverId(UUID.randomUUID().toString())
              .setAmountMinorUnits(amount)
              .setExpiresAt(Timestamp.newBuilder()
                    .setSeconds(Instant.now().plusSeconds(expirySeconds).getEpochSecond())
                    .build())
              .build();
    }

    @Nested
    @DisplayName("evaluate - Amount Threshold")
    class AmountThreshold {

        @Test
        @DisplayName("Should approve when amount is below threshold")
        void evaluate_AmountBelowThreshold() {
            MoneyTransferInitiated event = createEvent(50_000_000L, 600);
            when(velocityTracker.recordAndCheckVelocity(anyString(), anyString(), anyInt(), anyInt())).thenReturn(false);

            fraudDetectionService.evaluate(event);

            verify(fraudEventProducer).publishTransferApproved(event.getTransactionId());
        }

        @Test
        @DisplayName("Should detect fraud when amount exceeds threshold")
        void evaluate_AmountExceedsThreshold() {
            MoneyTransferInitiated event = createEvent(150_000_000L, 600);

            fraudDetectionService.evaluate(event);

            verify(fraudEventProducer).publishFraudDetected(eq(event.getTransactionId()), eq(event.getSenderId()), contains("exceeds threshold"));
            verify(velocityTracker, never()).recordAndCheckVelocity(anyString(), anyString(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("Should approve when amount exactly equals threshold")
        void evaluate_AmountEqualsThreshold() {
            MoneyTransferInitiated event = createEvent(100_000_000L, 600);
            when(velocityTracker.recordAndCheckVelocity(anyString(), anyString(), anyInt(), anyInt())).thenReturn(false);

            fraudDetectionService.evaluate(event);

            verify(fraudEventProducer).publishTransferApproved(event.getTransactionId());
        }
    }

    @Nested
    @DisplayName("evaluate - Velocity Check")
    class VelocityCheck {

        @Test
        @DisplayName("Should approve when velocity is within limits")
        void evaluate_VelocityWithinLimits() {
            MoneyTransferInitiated event = createEvent(50_000_000L, 600);
            when(velocityTracker.recordAndCheckVelocity(event.getSenderId(), event.getEventId(), 5, 10)).thenReturn(false);

            fraudDetectionService.evaluate(event);

            verify(fraudEventProducer).publishTransferApproved(event.getTransactionId());
        }

        @Test
        @DisplayName("Should detect fraud when velocity is exceeded")
        void evaluate_VelocityExceeded() {
            MoneyTransferInitiated event = createEvent(50_000_000L, 600);
            when(velocityTracker.recordAndCheckVelocity(event.getSenderId(), event.getEventId(), 5, 10)).thenReturn(true);

            fraudDetectionService.evaluate(event);

            verify(fraudEventProducer).publishFraudDetected(eq(event.getTransactionId()), eq(event.getSenderId()), contains("exceeded"));
        }

        @Test
        @DisplayName("Should use configured velocity settings")
        void evaluate_UsesConfiguredVelocity() {
            velocityConfig.setMaxTransactions(3);
            velocityConfig.setWindowMinutes(5);
            MoneyTransferInitiated event = createEvent(50_000_000L, 600);

            when(velocityTracker.recordAndCheckVelocity(event.getSenderId(), event.getEventId(), 3, 5)).thenReturn(false);

            fraudDetectionService.evaluate(event);

            verify(velocityTracker).recordAndCheckVelocity(event.getSenderId(), event.getEventId(), 3, 5);
        }
    }

    @Nested
    @DisplayName("evaluate - Expiry Check")
    class ExpiryCheck {

        @Test
        @DisplayName("Should publish FraudDetected when transaction is expired")
        void evaluate_TransactionExpired() {
            MoneyTransferInitiated event = createEvent(50_000_000L, -60);

            fraudDetectionService.evaluate(event);

            verify(fraudEventProducer).publishFraudDetected(event.getTransactionId(), event.getSenderId(), "transaction_expired");
            verify(velocityTracker, never()).recordAndCheckVelocity(anyString(), anyString(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("Should not record velocity when transaction is expired")
        void evaluate_ExpiredNoVelocityRecord() {
            MoneyTransferInitiated event = createEvent(50_000_000L, -1);

            fraudDetectionService.evaluate(event);

            verify(velocityTracker, never()).recordAndCheckVelocity(anyString(), anyString(), anyInt(), anyInt());
        }
    }

    @Nested
    @DisplayName("evaluate - Rule Priority")
    class RulePriority {

        @Test
        @DisplayName("Should check amount threshold before velocity")
        void evaluate_AmountBeforeVelocity() {
            MoneyTransferInitiated event = createEvent(150_000_000L, 600);

            fraudDetectionService.evaluate(event);

            verify(fraudEventProducer).publishFraudDetected(anyString(), anyString(), contains("exceeds threshold"));
            verify(velocityTracker, never()).recordAndCheckVelocity(anyString(), anyString(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("Should check expiry before fraud rules")
        void evaluate_ExpiryBeforeRules() {
            MoneyTransferInitiated event = createEvent(150_000_000L, -1);

            fraudDetectionService.evaluate(event);

            verify(fraudEventProducer).publishFraudDetected(anyString(), anyString(), eq("transaction_expired"));
        }
    }
}