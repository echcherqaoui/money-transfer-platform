-- ============================================================
-- V1 — transaction-service schema
-- Creates: transactions, outbox_event
-- ============================================================

-- ── transactions ────────────────────────────────────────────
CREATE TYPE transaction_status AS ENUM ('PENDING', 'COMPLETED', 'FAILED');

CREATE TABLE transactions (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    sender_id UUID NOT NULL,
    receiver_id UUID NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    status transaction_status NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT pk_transactions PRIMARY KEY (id),
    CONSTRAINT chk_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_sender_not_receiver CHECK (sender_id <> receiver_id)
);

CREATE INDEX idx_transactions_sender_id   ON transactions (sender_id);
CREATE INDEX idx_transactions_receiver_id ON transactions (receiver_id);
CREATE INDEX idx_transactions_status_created_at
    ON transactions (status, created_at)
    WHERE status = 'PENDING';


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
