package com.moneytransfer.security.service;

/**
 * Contract for event signature signing and verification.
 */
public interface ISignatureService {

    /**
     * Signs an event by computing a signature over its identifying fields.
     *
     * @param eventId        unique event ID
     * @param transactionId  the transaction this event belongs to
     * @param epochSeconds   event timestamp in epoch seconds (from occurred_at)
     * @return hex-encoded signature string
     */
    String sign(String eventId,
                String transactionId,
                String epochSeconds);

    /**
     * Verifies that a received signature matches the expected one.
     */
    boolean verify(String eventId,
                   String transactionId,
                   String epochSeconds,
                   String signature);
}