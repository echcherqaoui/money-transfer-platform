package com.moneytransfer.wallet.model;

import com.moneytransfer.wallet.enums.PendingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.hibernate.annotations.JdbcTypeCode;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static com.moneytransfer.wallet.enums.PendingStatus.INITIATED;
import static jakarta.persistence.EnumType.STRING;
import static org.hibernate.type.SqlTypes.NAMED_ENUM;

@Entity
@Table(
      name = "pending_transfer",
      indexes = {
            @Index(name = "idx_pending_transfer_sender_id", columnList = "sender_id"),
            @Index(name = "idx_pending_transfer_receiver_id", columnList = "receiver_id")
      }
)
@Setter
@Getter
@Accessors(chain = true)
@EntityListeners(AuditingEntityListener.class)
public class PendingTransfer {

    @Id
    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(name = "receiver_id", nullable = false)
    private UUID receiverId;

    @Enumerated(STRING)
    @Column(nullable = false, columnDefinition = "pending_status")
    @JdbcTypeCode(NAMED_ENUM)
    private PendingStatus status;

    @Column(nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @CreatedDate
    @Column(nullable = false, updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime updatedAt;

    @PrePersist
    private void setDefault(){
        this.status = INITIATED;
    }
}