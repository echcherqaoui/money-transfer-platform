package com.moneytransfer.fraud.service;

import com.moneytransfer.contract.MoneyTransferInitiated;

public interface IFraudDetectionService {
    // Evaluates the event against all fraud rules and publishes the outcome.
    void evaluate(MoneyTransferInitiated event);
}
