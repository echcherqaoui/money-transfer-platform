package com.moneytransfer.wallet.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum WalletRejectionCode {
    INSUFFICIENT_FUNDS("The sender does not have enough balance to cover the amount."),
    WALLET_NOT_FOUND("One or more participant wallets could not be located.");

    private final String description;
}