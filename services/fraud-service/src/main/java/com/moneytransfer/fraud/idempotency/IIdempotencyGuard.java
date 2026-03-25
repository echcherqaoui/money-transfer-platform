package com.moneytransfer.fraud.idempotency;

public interface IIdempotencyGuard {
    boolean isFirstOccurrence(String eventId);
}
