#!/bin/bash

set -a
source .env
set +a

envsubst '${PG_USER} ${PG_PASSWORD} ${PG_PORT} ${TRANSACTION_DB} ${PG_SERVICE}' < docker/connectors/transaction-outbox-connector.json | \
  curl -X POST http://localhost:${KF_CONNECT_PORT}/connectors -H "Content-Type: application/json" -d @-

envsubst '${PG_USER} ${PG_PASSWORD} ${PG_PORT} ${WALLET_DB} ${PG_SERVICE}' < docker/connectors/wallet-outbox-connector.json | \
  curl -X POST http://localhost:${KF_CONNECT_PORT}/connectors -H "Content-Type: application/json" -d @-