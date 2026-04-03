package com.moneytransfer.fraud.integration;

import com.google.protobuf.Timestamp;
import com.moneytransfer.contract.MoneyTransferInitiated;
import com.moneytransfer.fraud.properties.FraudProperties;
import com.moneytransfer.fraud.service.IFraudDetectionService;
import com.moneytransfer.fraud.service.IFraudEventProducer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka
@Testcontainers
@DisplayName("Fraud Detection Flow Integration Tests")
class FraudDetectionFlowIntegrationTest {
    @Autowired
    private IFraudDetectionService fraudDetectionService;
    @MockitoSpyBean
    private IFraudEventProducer fraudEventProducer;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private FraudProperties fraudProperties;

    @Container
    @ServiceConnection(name = "redis")
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:8.2.3-alpine"))
          .withExposedPorts(6379);

    @BeforeEach
    void setUp() {
        // Essential for isolation between @Test methods
        Assertions.assertNotNull(redisTemplate.getConnectionFactory());
        redisTemplate.getConnectionFactory().getConnection();
    }

    private MoneyTransferInitiated createEvent(String txId, String senderId, long amount, Instant expiry) {
        return MoneyTransferInitiated.newBuilder()
              .setEventId(UUID.randomUUID().toString())
              .setTransactionId(txId != null ? txId : UUID.randomUUID().toString())
              .setSenderId(senderId != null ? senderId : UUID.randomUUID().toString())
              .setReceiverId(UUID.randomUUID().toString())
              .setAmountMinorUnits(amount)
              .setExpiresAt(Timestamp.newBuilder().setSeconds(expiry.getEpochSecond()).build())
              .build();
    }

    @Test
    @DisplayName("Should approve valid transfer")
    void evaluate_ApproveValid() {
        String txId = UUID.randomUUID().toString();
        MoneyTransferInitiated event = createEvent(
              txId,
              null,
              50_000_000L,
              Instant.now().plusSeconds(600)
        );

        fraudDetectionService.evaluate(event);

        verify(fraudEventProducer).publishTransferApproved(txId);
    }

    @Test
    @DisplayName("Should detect fraud when amount exceeds threshold")
    void evaluate_AmountFraud() {
        String txId = UUID.randomUUID().toString();
        long amount = fraudProperties.getAmountThresholdMinorUnits() + 1;
        MoneyTransferInitiated event = createEvent(
              txId,
              null,
              amount,
              Instant.now().plusSeconds(600)
        );

        fraudDetectionService.evaluate(event);

        verify(fraudEventProducer)
              .publishFraudDetected(
                    eq(txId),
                    anyString(),
                    contains("exceeds threshold")
              );
    }

    @Test
    @DisplayName("Should detect velocity fraud")
    void evaluate_VelocityFraud() {
        String senderId = UUID.randomUUID().toString();
        int max = fraudProperties.getVelocity().getMaxTransactions();
        Instant expiry = Instant.now().plusSeconds(600);

        for (int i = 0; i <= max; i++)
            fraudDetectionService.evaluate(
                  createEvent(
                        null,
                        senderId,
                        50_000_000L,
                        expiry
                  )
            );


        verify(fraudEventProducer)
              .publishFraudDetected(
                    anyString(),
                    eq(senderId),
                    contains("exceeded")
              );
    }

    @Test
    @DisplayName("Should publish FraudDetected when transaction is expired")
    void evaluate_Expired() {
        String txId = UUID.randomUUID().toString();
        MoneyTransferInitiated event = createEvent(
              txId,
              null,
              50_000_000L,
              Instant.now().minusSeconds(60)
        );

        fraudDetectionService.evaluate(event);

        verify(fraudEventProducer).publishFraudDetected(
              txId,
              event.getSenderId(),
              "transaction_expired"
        );
    }
}