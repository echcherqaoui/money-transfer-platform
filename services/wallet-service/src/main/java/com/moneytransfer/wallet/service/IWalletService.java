package com.moneytransfer.wallet.service;

import com.moneytransfer.wallet.dto.WalletResponse;
import org.springframework.transaction.annotation.Transactional;

public interface IWalletService {
    WalletResponse getMyWallet();

    @Transactional
    WalletResponse createWallet();
}