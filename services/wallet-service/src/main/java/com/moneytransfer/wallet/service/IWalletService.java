package com.moneytransfer.wallet.service;

import com.moneytransfer.wallet.dto.WalletResponse;

public interface IWalletService {
    WalletResponse getMyWallet();

    WalletResponse createWallet();
}