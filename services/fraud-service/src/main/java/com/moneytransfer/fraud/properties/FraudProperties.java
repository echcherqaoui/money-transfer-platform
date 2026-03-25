package com.moneytransfer.fraud.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "fraud.rules")
@Setter
@Getter
public class FraudProperties {
    private long amountThresholdMinorUnits = 100_000_000L;    // Maximum allowed amount in minor units
    private Velocity velocity = new Velocity();

    @Setter
    @Getter
    public static class Velocity {
        private int maxTransactions = 5;        // Maximum number of transfers allowed within the time window.
        private int windowMinutes = 10;        // Sliding window duration in minutes.
    }
}