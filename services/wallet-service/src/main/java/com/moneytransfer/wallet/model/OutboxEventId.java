package com.moneytransfer.wallet.model;

import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

@EqualsAndHashCode
public class OutboxEventId implements Serializable {
    private UUID id;
    private OffsetDateTime createdAt;
}