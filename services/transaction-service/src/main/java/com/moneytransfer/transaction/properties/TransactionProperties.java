package com.moneytransfer.transaction.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "transaction.cleanup")
@Setter
@Getter
public class TransactionProperties {
    private long timeoutMinutes = 2;
}