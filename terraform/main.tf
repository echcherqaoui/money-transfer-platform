locals {
	# Prefix for all topics
	prefix = "dev.mtp"
	retention_7_days = 604800000
	standard_partitions = 3
}

resource "kafka_topic" "debezium_heartbeat" {
  name               = "__debezium-heartbeat.dev.mtp.transaction"
  partitions         = 1
  replication_factor = 1
  config = {
    "cleanup.policy" = "delete"
    "retention.ms"   = "3600000" # 1 hour is enough;
  }
}

resource "kafka_topic" "transfer_initiated" {
  name               = "${local.prefix}.transaction.transfer.initiated.v1"
  replication_factor = 1 # single broker — dev only, increase for production
  partitions         = local.standard_partitions
  config = {
    "cleanup.policy" = "delete"
    "retention.ms"   = local.retention_7_days
  }
}

resource "kafka_topic" "transfer_initiated_dlt" {
  name               = "${local.prefix}.transaction.transfer.initiated.v1.DLT"
  replication_factor = 1
  partitions         = 1  # DLT doesn't need multiple partitions
  config = {
    "cleanup.policy" = "delete"
    "retention.ms"   = local.retention_7_days
  }
}

resource "kafka_topic" "fraud_detected" {
  name               = "${local.prefix}.transaction.transfer.fraud.detected.v1"
  replication_factor = 1 # single broker — dev only, increase for production
  partitions         = local.standard_partitions
  config = {
    "cleanup.policy" = "delete"
    "retention.ms"   = local.retention_7_days
  }
}

resource "kafka_topic" "transfer_approved" {
  name               = "${local.prefix}.transaction.transfer.approved.v1"
  replication_factor = 1 # single broker — dev only, increase for production
  partitions         = local.standard_partitions
  config = {
    "cleanup.policy" = "delete"
    "retention.ms"   = local.retention_7_days
  }
}