package com.moneytransfer.wallet.service;

import com.moneytransfer.contract.FraudDetected;
import com.moneytransfer.contract.MoneyTransferInitiated;
import com.moneytransfer.contract.TransferApproved;

public interface ITransferOrchestrator {
    void handleInitiated(MoneyTransferInitiated event);

    void handleApproved(TransferApproved event);

    void handleFraud(FraudDetected event);
}
