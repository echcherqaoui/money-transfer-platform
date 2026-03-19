locals {
	# Prefix for all topics
	prefix = "mtp"
	retention_7_days = 604800000
	standard_partitions = 3
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