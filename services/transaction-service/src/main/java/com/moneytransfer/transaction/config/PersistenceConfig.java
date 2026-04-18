package com.moneytransfer.transaction.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.OffsetDateTime;
import java.util.Optional;

import static java.time.ZoneOffset.UTC;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "offsetDateTimeProvider")
public class PersistenceConfig {
    @Bean
    public DateTimeProvider offsetDateTimeProvider() {
        // This ensures the auditor provides OffsetDateTime.now()
        return () -> Optional.of(OffsetDateTime.now(UTC));
    }
}