package com.moneytransfer.fraud.service;

import com.moneytransfer.contract.MoneyTransferInitiated;

public interface IFraudDetectionService {
    void evaluate(MoneyTransferInitiated event);
}
