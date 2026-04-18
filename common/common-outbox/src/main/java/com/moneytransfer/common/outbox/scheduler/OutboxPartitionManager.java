package com.moneytransfer.common.outbox.scheduler;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
public class OutboxPartitionManager {

    private static final String PARTITION_PREFIX = "outbox_p";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy_MM_dd");
    private static final Pattern PARTITION_NAME_PATTERN = Pattern.compile("^outbox_p\\d{4}_\\d{2}_\\d{2}$");

    private final JdbcTemplate jdbcTemplate;
    private final int retentionDays;
    private final int futurePartitionDays;

    public OutboxPartitionManager(JdbcTemplate jdbcTemplate,
                                  @Value("${outbox.partition.retention-days:7}") int retentionDays,
                                  @Value("${outbox.partition.future-days:2}") int futurePartitionDays) {
        this.jdbcTemplate = jdbcTemplate;
        this.retentionDays = retentionDays;
        this.futurePartitionDays = futurePartitionDays;
    }

    @PostConstruct
    public void ensurePartitionsExist() {
        try {
            createFuturePartitions();
            log.info("Partitions initialized successfully");
        } catch (Exception e) {
            log.error("FATAL: Failed to initialize partitions", e);
            throw new IllegalStateException("Cannot start without partitions", e);
        }
    }

    private String sanitizeIdentifier(String identifier) {
        if (!identifier.matches("^[a-z_][a-z0-9_]*$"))
            throw new IllegalArgumentException("Invalid SQL identifier: " + identifier);

        return identifier;
    }

    private void dropPartition(String partitionName) {
        try {
            jdbcTemplate.execute(String.format(
                  "DROP TABLE IF EXISTS %s",
                  sanitizeIdentifier(partitionName)
            ));
            log.info("Dropped expired partition: {}", partitionName);
        } catch (DataAccessException e) {
            log.error("Failed to drop partition: {}", partitionName, e);
            throw e;
        }
    }

    private void dropExpiredPartitions() {
        LocalDate cutoffDate = LocalDate.now().minusDays(retentionDays);

        List<String> partitions = jdbcTemplate.queryForList(
              """
                      SELECT child.relname
                      FROM pg_inherits i
                      JOIN pg_class parent ON parent.oid = i.inhparent
                      JOIN pg_class child ON child.oid = i.inhrelid
                      WHERE parent.relname = 'outbox_event'
                        AND child.relname LIKE 'outbox_p%'
                """,
              String.class
        );

        for (String partitionName : partitions) {
            if (!PARTITION_NAME_PATTERN.matcher(partitionName).matches()) {
                log.warn("Skipping partition with unexpected name format: {}", partitionName);
                continue;
            }

            try {
                LocalDate partitionDate = extractDateFromPartitionName(partitionName);

                if (partitionDate.isBefore(cutoffDate))
                    dropPartition(partitionName);
            } catch (DateTimeParseException e) {
                log.error("Failed to parse date from partition name: {}", partitionName, e);
            }
        }
    }

    private LocalDate extractDateFromPartitionName(String partitionName) {
        String datePart = partitionName.substring(PARTITION_PREFIX.length());
        return LocalDate.parse(datePart, DATE_FORMATTER);
    }

    private void createPartition(LocalDate date) {
        String partitionName = PARTITION_PREFIX + date.format(DATE_FORMATTER);
        LocalDate nextDay = date.plusDays(1);

        try {
            jdbcTemplate.execute(String.format(
                  "CREATE TABLE IF NOT EXISTS %s PARTITION OF outbox_event FOR VALUES FROM ('%s') TO ('%s')",
                  sanitizeIdentifier(partitionName),
                  date,
                  nextDay
            ));

            log.info(
                  "Created partition {} for date range [{}, {})",
                  partitionName,
                  date,
                  nextDay
            );
        } catch (DataAccessException e) {
            log.error(
                  "Failed to create partition {} for date {}",
                  partitionName,
                  date,
                  e
            );
            throw e;
        }
    }

    private void createFuturePartitions() {
        LocalDate today = LocalDate.now();

        for (int i = 0; i <= futurePartitionDays; i++)
            createPartition(today.plusDays(i));
    }

    @Scheduled(cron = "${outbox.partition.cron:0 0 0 * * *}", zone = "UTC")
    public void managePartitions() {
        try {
            createFuturePartitions();
            dropExpiredPartitions();
            log.info("Partition management completed successfully");
        } catch (Exception e) {
            log.error("Partition management failed", e);
        }
    }
}