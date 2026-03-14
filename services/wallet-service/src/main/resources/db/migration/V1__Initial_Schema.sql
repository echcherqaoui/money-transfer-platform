-- ============================================================
-- V1 — wallet-service schema
-- Creates: wallets, processed_events
-- ============================================================

-- ── wallets ──────────────────────────────────────────────────
CREATE TABLE wallets (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    balance NUMERIC(19, 4) NOT NULL DEFAULT 0.0000,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT pk_wallets PRIMARY KEY (id),
    CONSTRAINT uq_wallets_user_id UNIQUE (user_id),
    CONSTRAINT chk_balance_non_negative CHECK (balance >= 0)
);

CREATE INDEX idx_wallets_user_id ON wallets (user_id);


-- ── processed_events ─────────────────────────────────────────
-- Idempotency guard for Kafka consumers.
-- Inserted atomically in the same DB transaction as the balance update.

CREATE TABLE processed_events (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    event_id VARCHAR(255) NOT NULL,  -- Kafka message key / Protobuf event ID
    event_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT pk_processed_events PRIMARY KEY (id),
    CONSTRAINT uq_processed_event_id UNIQUE (event_id)
);

CREATE INDEX idx_processed_events_event_id ON processed_events (event_id);