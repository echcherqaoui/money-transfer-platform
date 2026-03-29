package com.moneytransfer.transaction.service;

import com.moneytransfer.transaction.model.Transaction;

public interface ITransactionOutboxService {
    void publishTransferInitiated(Transaction transaction);
}
