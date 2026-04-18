package com.moneytransfer.wallet.service.impl;

import com.moneytransfer.wallet.dto.BalanceUpdateEvent;
import com.moneytransfer.wallet.dto.WalletResponse;
import com.moneytransfer.wallet.model.DepositLog;
import com.moneytransfer.wallet.model.Wallet;
import com.moneytransfer.wallet.repository.DepositLogRepository;
import com.moneytransfer.wallet.service.IDepositLimitService;
import com.moneytransfer.wallet.service.IDepositService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DepositServiceImpl implements IDepositService {

    private final DepositLogRepository depositLogRepository;
    private final WalletService walletService;
    private final IDepositLimitService limitService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    @Override
    public WalletResponse deposit(UUID userId, BigDecimal amount) {
        limitService.validate(userId, amount);

        Wallet wallet = walletService.credit(userId, amount);

        depositLogRepository.save(
              new DepositLog()
                    .setUserId(userId)
                    .setAmount(amount)
        );

        eventPublisher.publishEvent(new BalanceUpdateEvent(wallet.getUserId(), wallet.getBalance()));

        log.info(
              "[DEPOSIT] user={} amount={} newBalance={}",
              userId,
              amount,
              wallet.getBalance()
        );

        return WalletResponse.from(wallet);
    }
}