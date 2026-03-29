package com.moneytransfer.wallet.service.impl;

import com.moneytransfer.wallet.exception.WalletException;
import com.moneytransfer.wallet.properties.DepositProperties;
import com.moneytransfer.wallet.repository.DepositLogRepository;
import com.moneytransfer.wallet.service.IDepositLimitService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static com.moneytransfer.wallet.exception.enums.WalletErrorCode.DAILY_DEPOSIT_LIMIT_EXCEEDED;
import static com.moneytransfer.wallet.exception.enums.WalletErrorCode.DEPOSIT_LIMIT_EXCEEDED;

@Service
@RequiredArgsConstructor
public class DepositLimitService implements IDepositLimitService {

    private final DepositLogRepository depositLogRepository;
    private final DepositProperties depositProperties;

    private BigDecimal fromMinorUnits(long minorUnits) {
        return BigDecimal.valueOf(minorUnits).movePointLeft(4);
    }

    private void validateSingleLimit(BigDecimal amount) {
        BigDecimal limit = fromMinorUnits(depositProperties.getMaxSingleDepositMinorUnits());

        if (amount.compareTo(limit) > 0)
            throw new WalletException(DEPOSIT_LIMIT_EXCEEDED, depositProperties.getMaxSingleDepositMinorUnits());
    }

    private void validateDailyLimit(UUID userId, BigDecimal amount) {
        OffsetDateTime startOfDay = OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS);
        BigDecimal depositedToday = depositLogRepository.sumDepositedToday(userId, startOfDay);
        BigDecimal limit = fromMinorUnits(depositProperties.getMaxDailyDepositMinorUnits());

        if (depositedToday.add(amount).compareTo(limit) > 0)
            throw new WalletException(DAILY_DEPOSIT_LIMIT_EXCEEDED, depositProperties.getMaxDailyDepositMinorUnits());

    }

    @Override
    public void validate(UUID userId, BigDecimal amount) {
        validateSingleLimit(amount);
        validateDailyLimit(userId, amount);
    }
}