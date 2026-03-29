# Resource labels changed, but physical topic name is the same
moved {
  from = kafka_topic.transfer_initiated
  to   = kafka_topic.transaction_transfer_initiated
}

moved {
  from = kafka_topic.debezium_heartbeat
  to   = kafka_topic.debezium_heartbeat_transaction
}

moved {
  from = kafka_topic.wallet_heartbeat
  to   = kafka_topic.debezium_heartbeat_wallet
}

moved {
  from = kafka_topic.transfer_initiated_dlt
  to   = kafka_topic.transaction_transfer_initiated_dlt
}