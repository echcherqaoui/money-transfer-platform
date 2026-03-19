#!/bin/bash

set -a
source .env
set +a

# Add PG_SERVICE to the list here
envsubst '${PG_USER} ${PG_PASSWORD} ${PG_PORT} ${TRANSACTION_DB} ${PG_SERVICE}' < docker/connectors/transaction-outbox-connector.json | \
  curl -X POST http://localhost:${KF_CONNECT_PORT}/connectors -H "Content-Type: application/json" -d @-