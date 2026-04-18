package com.moneytransfer.transaction.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TransactionRejectionCode {
    
    TRANSACTION_EXPIRED("The transaction time limit was exceeded before processing could complete");

    private final String description;

}