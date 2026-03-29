locals {
	# Prefix for all topics
	prefix = "dev.mtp"
	retention_7_days = 604800000
	retention_1_hour    = 3600000
	standard_partitions = 3
}

# TRANSACTION SERVICE TOPICS
# ─────────────────────────────────────────────────────────────────────────────
resource "kafka_topic" "transaction_transfer_initiated" {
  name               = "${local.prefix}.transaction.transfer.initiated.v1"
  replication_factor = 1 # single broker — dev only, increase for production
  partitions         = local.standard_partitions
  config = {
    "cleanup.policy" = "delete"
    "retention.ms"   = local.retention_7_days
  }

  lifecycle {
    prevent_destroy = true
  }
}

resource "kafka_topic" "transaction_transfer_initiated_dlt" {
  name               = "${local.prefix}.transaction.transfer.initiated.v1-dlt"
  replication_factor = 1
  partitions         = 1  # DLT doesn't need multiple partitions
  config = {
    "cleanup.policy" = "delete"
    "retention.ms"   = local.retention_7_days
  }
}

# FRAUD SERVICE TOPICS
# ─────────────────────────────────────────────────────────────────────────────
resource "kafka_topic" "fraud_transfer_detected" {
  name               = "${local.prefix}.fraud.transfer.detected.v1"
  replication_factor = 1 # single broker — dev only, increase for production
  partitions         = local.standard_partitions
  config = {
    "cleanup.policy" = "delete"
    "retention.ms"   = local.retention_7_days
  }

  lifecycle {
    prevent_destroy = true
  }
}

resource "kafka_topic" "fraud_transfer_approved" {
  name               = "${local.prefix}.fraud.transfer.approved.v1"
  replication_factor = 1 # single broker — dev only, increase for production
  partitions         = local.standard_partitions
  config = {
    "cleanup.policy" = "delete"
    "retention.ms"   = local.retention_7_days
  }

  lifecycle {
    prevent_destroy = true
  }
}


resource "kafka_topic" "fraud_transfer_approved_dlt" {
  name               = "${local.prefix}.fraud.transfer.approved.v1-dlt"
  replication_factor = 1
  partitions         = 1
  config = {
    "cleanup.policy" = "delete"
    "retention.ms"   = local.retention_7_days
  }
}

# WALLET SERVICE TOPICS
# ─────────────────────────────────────────────────────────────────────────────

resource "kafka_topic" "wallet_transfer_completed" {
  name               = "${local.prefix}.wallet.transfer.completed.v1"
  replication_factor = 1 # single broker — dev only, increase for production
  partitions         = local.standard_partitions
  config = {
    "cleanup.policy" = "delete"
    "retention.ms"   = local.retention_7_days
  }

  lifecycle {
    prevent_destroy = true
  }
}

resource "kafka_topic" "wallet_transfer_failed" {
  name               = "${local.prefix}.wallet.transfer.failed.v1"
  replication_factor = 1
  partitions         = local.standard_partitions
  config = {
    "cleanup.policy" = "delete"
    "retention.ms"   = local.retention_7_days
  }

  lifecycle {
    prevent_destroy = true
  }
}

# DEBEZIUM CDC HEARTBEAT TOPICS
# ─────────────────────────────────────────────────────────────────────────────

resource "kafka_topic" "debezium_heartbeat_transaction" {
  name               = "__debezium-heartbeat.${local.prefix}.transaction"
  replication_factor = 1
  partitions         = 1
  config = {
    "cleanup.policy" = "delete"
    "retention.ms"   = local.retention_1_hour
  }
}

resource "kafka_topic" "debezium_heartbeat_wallet" {
  name               = "__debezium-heartbeat.${local.prefix}.wallet"
  replication_factor = 1
  partitions         = 1
  config = {
    "cleanup.policy" = "delete"
    "retention.ms"   = local.retention_1_hour
  }
}