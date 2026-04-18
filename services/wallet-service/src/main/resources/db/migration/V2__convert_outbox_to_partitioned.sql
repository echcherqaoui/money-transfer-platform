-- Drop existing non-partitioned table
-- WARNING: This destroys all existing outbox data
DROP TABLE IF EXISTS outbox_event CASCADE;

-- Create partitioned outbox table
CREATE TABLE outbox_event
(
    id             UUID                     NOT NULL DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100)             NOT NULL,
    aggregate_id   UUID                     NOT NULL,
    event_type     VARCHAR(100)             NOT NULL,
    payload        BYTEA                    NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    -- Composite PK required: PostgreSQL mandates partition key in PK for RANGE partitioning
    CONSTRAINT pk_outbox_event PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

-- Recreate indexes that were dropped with CASCADE
CREATE INDEX idx_outbox_event_aggregate ON outbox_event (aggregate_type, aggregate_id);

-- Safely drop if it exists to ensure we apply the correct settings
DROP PUBLICATION IF EXISTS wallet_outbox_publication;

-- Create CDC publication with partition-root publishing
-- Debezium sees all partition writes as parent table operations
CREATE PUBLICATION wallet_outbox_publication
    FOR TABLE outbox_event
    WITH (publish_via_partition_root = true);

-- Set replication identity to use primary key for CDC tracking
ALTER TABLE outbox_event REPLICA IDENTITY DEFAULT;