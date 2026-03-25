package com.moneytransfer.fraud.velocity;

public interface IVelocityTracker {
    // Records the request and returns true if velocity is exceeded.
    boolean recordAndCheckVelocity(String senderId,
                                   String eventId,
                                   int maxTransactions,
                                   int windowMinutes);
}
