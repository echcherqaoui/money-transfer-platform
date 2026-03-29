package com.moneytransfer.wallet.idempotency;

public interface IIdempotencyGuard {
    boolean isProcessed(String eventId);
}
