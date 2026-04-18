package com.moneytransfer.wallet.service.impl;

import com.moneytransfer.security.jwt.JwtUtils;
import com.moneytransfer.wallet.dto.WalletResponse;
import com.moneytransfer.wallet.exception.WalletException;
import com.moneytransfer.wallet.model.Wallet;
import com.moneytransfer.wallet.repository.WalletRepository;
import com.moneytransfer.wallet.service.IWalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static com.moneytransfer.wallet.exception.enums.WalletErrorCode.WALLET_ALREADY_EXISTS;
import static com.moneytransfer.wallet.exception.enums.WalletErrorCode.WALLET_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class WalletService implements IWalletService {

    private final WalletRepository walletRepository;

    @Transactional(readOnly = true)
    @Override
    public WalletResponse getMyWallet() {
        UUID currentUserId = JwtUtils.extractUserId();

        return walletRepository.findByUserId(currentUserId)
              .map(WalletResponse::from)
              .orElseThrow(() -> new WalletException(WALLET_NOT_FOUND, currentUserId));
    }

    @Transactional
    @Override
    public WalletResponse createWallet() {
        UUID userId = JwtUtils.extractUserId();

        if (walletRepository.existsByUserId(userId))
            throw new WalletException(WALLET_ALREADY_EXISTS, userId);

        Wallet wallet = new Wallet()
              .setUserId(userId)
              .setBalance(BigDecimal.ZERO);

        return WalletResponse.from(walletRepository.save(wallet));
    }

    @Transactional
    public Wallet credit(UUID userId, BigDecimal amount) {
        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
              .orElseThrow(() -> new WalletException(WALLET_NOT_FOUND, userId));

        wallet.credit(amount);
        return walletRepository.save(wallet);
    }


    @Transactional(readOnly = true)
    @Override
    public boolean hasWallet(UUID userId) {
        return walletRepository.existsByUserId(userId);
    }
}