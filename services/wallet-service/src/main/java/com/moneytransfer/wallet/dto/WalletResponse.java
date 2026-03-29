package com.moneytransfer.wallet.dto;

import com.moneytransfer.wallet.model.Wallet;

import java.math.BigDecimal;
import java.util.UUID;

public record WalletResponse(UUID userId,
                             BigDecimal balance) {

    public static WalletResponse from(Wallet wallet){
        return new WalletResponse(
              wallet.getUserId(),
              wallet.getBalance()
        );
    }
}
