-- ============================================================
-- V1 — wallet-service schema
-- Creates: wallets, pending_transfer, processed_events, outbox_event
-- ============================================================

-- ── pending_status ──────────────────────────────────────────
CREATE TYPE pending_status AS ENUM ('INITIATED', 'COMPLETED', 'FAILED', 'DISCARDED');

-- ── wallets ──────────────────────────────────────────────────
CREATE TABLE wallets (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    balance NUMERIC(19, 4) NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT pk_wallets PRIMARY KEY (id),
    CONSTRAINT uq_wallets_user_id UNIQUE (user_id),
    CONSTRAINT chk_balance_non_negative CHECK (balance >= 0)
);

CREATE INDEX idx_wallets_user_id ON wallets (user_id);

-- ── pending_transfer ─────────────────────────────────────────
CREATE TABLE pending_transfer (
    transaction_id UUID NOT NULL,
    sender_id UUID NOT NULL,
    receiver_id UUID NOT NULL,
    status pending_status NOT NULL DEFAULT 'INITIATED',
    amount NUMERIC(19, 4) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT pk_pending_transfer PRIMARY KEY (transaction_id),
    CONSTRAINT chk_pending_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_pending_sender_not_receiver CHECK (sender_id <> receiver_id)
);

CREATE INDEX idx_pending_transfer_sender_id ON pending_transfer (sender_id);
CREATE INDEX idx_pending_transfer_receiver_id ON pending_transfer (receiver_id);

-- ── processed_events ─────────────────────────────────────────
-- Idempotency guard for Kafka consumers.
CREATE TABLE processed_events (
    event_id VARCHAR(36) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT pk_processed_events PRIMARY KEY (event_id)
);

-- ── outbox_event ─────────────────────────────────────────────
CREATE TABLE outbox_event (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload BYTEA NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

CONSTRAINT pk_outbox_event PRIMARY KEY (id)
);

CREATE INDEX idx_outbox_event_aggregate ON outbox_event (aggregate_type, aggregate_id);
CREATE INDEX idx_outbox_created_at ON outbox_event (created_at);

-- ── deposit_log ───────────────────────────────────────────────
-- Admin-only top-up
CREATE TABLE deposit_log (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    deposited_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT pk_deposit_log PRIMARY KEY (id),
    CONSTRAINT chk_deposit_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_deposit_log_user_day ON deposit_log (user_id, deposited_at);