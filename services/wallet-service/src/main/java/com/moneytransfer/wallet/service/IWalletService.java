package com.moneytransfer.wallet.service;

import com.moneytransfer.wallet.dto.WalletResponse;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public interface IWalletService {
    WalletResponse getMyWallet();

    WalletResponse createWallet();

    @Transactional(readOnly = true)
    boolean hasWallet(UUID userId);
}