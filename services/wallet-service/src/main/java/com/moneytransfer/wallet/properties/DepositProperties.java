package com.moneytransfer.wallet.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "wallet.deposit")
@Getter
@Setter
public class DepositProperties {
    /** Maximum single deposit in minor units. Default: 100_000_00 = 1000,000 */
    private long maxSingleDepositMinorUnits = 10_000_000L;
    /** Maximum cumulative daily deposit in minor units. Default: 500_000_00 = 5000,000 */
    private long maxDailyDepositMinorUnits = 50_000_000L;
}