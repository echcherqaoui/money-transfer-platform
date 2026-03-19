terraform {
  required_providers {
    kafka = {
      source  = "Mongey/kafka"
      version = "0.12.1"
    }
  }
}

provider "kafka" {
  bootstrap_servers = [var.kafka_broker]
  tls_enabled       = false
  skip_tls_verify   = true
}