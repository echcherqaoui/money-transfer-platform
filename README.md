# Money Transfer Platform

A Distributed money transfer system built with Spring Boot microservices, event-driven architecture, and production-level patterns including outbox/CDC, choreography saga, HMAC event security, and real-time SSE balance updates.

---

## Project Highlights

The Money Transfer Platform simulates a real-world fintech backend where users can transfer funds between wallets. The platform is built as a set of independently deployable microservices that communicate exclusively through Kafka events — no synchronous inter-service calls exist anywhere in the system.

Key Patterns Implemented:

- Transactional Outbox + CDC — Atomically guaranteed event delivery via Debezium WAL streaming
- Choreography Saga — Self-healing distributed workflows with embedded expiresAt for autonomous expiry detection
- HMAC Event Signing — Cryptographic integrity on every Kafka message to prevent tampering
- Idempotency Guarantees — DB primary key constraints (wallet) + Redis SET NX (fraud) for exactly-once processing
- Pessimistic Locking — Deadlock-free settlement via sorted UUID lock ordering
  BFF Pattern — Session-based auth with server-side JWT storage in Redis

---
## Architecture & Event Flow

```text
User → Gateway (BFF) → Transaction Service
                            │
                            ├── PostgreSQL: INSERT transaction (PENDING)
                            └── PostgreSQL: INSERT outbox_event
                                                 │
                                           Debezium CDC
                                                 │
                                                 ▼
                                    Kafka: MoneyTransferInitiated
                                    (contains expiresAt for saga safety)
                                          │              │
                                   ┌──────┘              └──────┐
                                   ▼                            ▼
                            Fraud Service               Wallet Service
                            (evaluate rules)            (store pending)
                                   │
                          ┌────────┴────────┐
                          ▼                 ▼
                   TransferApproved   FraudDetected
                          │                 │
                          └────────┬────────┘
                                   ▼
                            Wallet Service
                            (settle or discard)
                                   │
                          ┌────────┴────────┐
                          ▼                 ▼
                 TransferCompleted    TransferFailed
                 (Debezium/Outbox)    (Debezium/Outbox)
                          │
                          ▼
                 Transaction Service
                 (mark COMPLETED/FAILED + SSE notify)
```
---

## Tech Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Java | 17 |
| Framework | Spring Boot | 3.5.11 |
| Cloud | Spring Cloud | 2025.0.1 |
| Database | PostgreSQL | 16 |
| Migrations | Flyway | 11.7.2 |
| Messaging | Apache Kafka (KRaft) | — |
| CDC | Debezium PostgreSQL Connector | 3.1.2 |
| Schema Registry | Confluent | 7.7.7 |
| Cache | Redis | 8.2.3-alpine |
| Authentication | Keycloak | 26.0.6 |
| Infra-as-code | Terraform (Mongey/kafka) | 0.12.1 |
| Observability | Prometheus + Tempo + Grafana | — |
| Migrations | Flyway | 11.7.2|

---

## Project Structure

```
money-transfer-platform/
|
├── common                          
│   ├── common-exceptions           # Exception hierarchy, global handler, auto-configured
│   ├── common-security             # HMAC service, JWT utilities, auto-configured
│   └── transfer-api-contract       # Protobuf event contracts, Schema Registry plugin
├── docker                          
│   ├── app.yml                     # Docker Compose configuration for business services
│   ├── connectors                  # JSON configs for Debezium outbox and sink connectors
│   ├── dev-tools.yml               # Auxiliary tools like Kafka UI and pgAdmin
│   ├── grafana                     # Dashboards and provisioning for metrics visualization
│   ├── infra.yml                   # Core dependencies including Kafka, Postgres, and Redis
│   ├── init-db.sql                 # SQL scripts for database creation
│   ├── kafka-connect.Dockerfile    
│   ├── prometheus.yml             
│   ├── promtail-config.yml         # Log collection configuration for Grafana Loki
│   ├── tempo.yml                   # Distributed tracing backend configuration
│   └── tools.yml                   # Containerized Terraform and CLI utility environment
├── infrastructure                  
│   └── api-gateway                 # Reactive BFF for JWT translation and service routing
├── Makefile                        # Automation entry point for build and deployment tasks
├── services                        # Business domain microservices
│   ├── fraud-service               # Fraud rules, velocity tracking
│   ├── transaction-service         # Transfer initiation, timeout compensation
│   └── wallet-service              # Balance management, settlement, SSE
└── terraform                       
    ├── main.tf                     # Definitions for Kafka topics and schema registration
    ├── migration.tf                
    ├── provider.tf                
    └── variables.tf            

```

---

## Services

### API Gateway

**Port:** `8080` | **Management:** `8070`  | **Data Store:** `Redis`

Implements the Backend-for-Frontend (BFF) pattern. The browser never holds a JWT — it receives an `HttpOnly` session cookie. The JWT is stored server-side in Redis and forwarded to downstream services via `TokenRelay`.

### Transaction Service

**Port:** `8040` | **Management:** `8070`  | **Data Store:** `PostgreSQL`

Entry point for all money transfers. Validates the request, persists the transaction, and publishes `MoneyTransferInitiated` via the Transactional Outbox Pattern.

### Fraud Service

**Port:** `8050` | **Management:** `8070`  | **Data Store:** `Redis`

Pure event processor. Consumes `MoneyTransferInitiated`, evaluates fraud rules, and publishes either `TransferApproved` or `FraudDetected`. Uses Redis for idempotency and velocity tracking.

### Wallet Service

**Port:** `8045` | **Management:** `8070`  | **Data Store:** `Redis`

Manages user balances. Consumes events from both fraud-service and transaction-service. Publishes outcomes via the Transactional Outbox Pattern (Debezium).

---
## Getting Started
### Prerequisites

- Docker + Docker Compose
- Java 17 (for local builds)
- Make (macOS/Linux built-in)


### 1. Clone the repository
```bash
git clone https://github.com/echcherqaoui/money-transfer-platform.git
cd money-transfer-platform

# Copy environment template
cp .env.example .env
```
### 2. Start infrastructure
```bash
make up-infra
```

Services started:
- PostgreSQL (:5432) — transaction_db, wallet_db
- Keycloak (:8443) — OIDC provider
- Redis (:6379) — session store + idempotency
- Kafka (:9096) — event backbone
- Schema Registry (:8181) — Protobuf schema versioning
- Kafka Connect (:8183) — Debezium CDC
- Kafka UI (:8800) — topic inspection
- pgAdmin (:8000) — database GUI

Wait for health checks (30-60s). Verify: docker ps — all containers should show "healthy".

---

## Configure Keycloak

1. Navigate to http://localhost:8443
2. Log in with admin credentials
3. Create Realm:
    - Name: money-transfer
4. Create Client:
    - Client ID: money-transfer-gateway
    - Client authentication: ON
    - Standard flow: ENABLED
    - Valid redirect URIs: http://localhost:8080/login/oauth2/code/keycloak
    - Save → Copy Client Secret → paste into .env as KC_CLIENT_SECRET
---

### 3. Register Protobuf schemas
```bash
# Register Protobuf schemas to Schema Registry
make register-schemas

# Create Kafka topics via Terraform
make topics-apply

# Register Debezium outbox connectors
make register-connectors
```
> Note: Only run once on first setup. Re-run register-schemas if .proto files change.

### 4. Build and start the application
```bash
make rebuild-all
```

Builds all Maven modules, builds Docker images, and starts all services.

Services started:

- api-gateway: 8080
- transaction-service: 8040
- fraud-service: 8050
- wallet-service: 8045
---

## Available Make Commands
```bash
# 1. Start all infrastructure
make up-infra

# 2. Register Protobuf schemas to Schema Registry
make register-schemas

# 3. Create Kafka topics via Terraform
make topics-apply

# 4. Register Debezium connectors
make register-connectors

# 5. Start all services
make up-app

# Or start everything at once (dev)
make up-dev

# 6. Stop and remove all containers
make down
```

---

## Design Decisions

### Why RecordNameStrategy for Schema Registry?

Topic names are environment-prefixed (`dev.mtp.xxx`). The default `TopicNameStrategy` derives subject names from topic names, making the schema contract environment-aware. `RecordNameStrategy` uses the Protobuf package name (`com.moneytransfer.X`), which is environment-independent. Schemas are registered once and work across all environments without modification.

### Why Outbox Pattern + Debezium, not direct KafkaTemplate?

Publishing directly to Kafka after a database write creates a dual-write problem — if the service crashes between the DB commit and the Kafka publish, the event is lost with no recovery path. The Transactional Outbox Pattern writes the event payload to a DB table in the same transaction as the business data. Debezium reads the PostgreSQL WAL (not the table directly) and publishes to Kafka. Atomicity is guaranteed by the database transaction.

### Why fraud-service uses KafkaTemplate directly?

Fraud-service has no database. The outbox pattern solves dual-write atomicity between a DB write and a Kafka publish. With no DB write, there is nothing to be atomic with.

### Why expiresAt is embedded in MoneyTransferInitiated?

The alternative — publishing a separate `TransferExpired` event from the timeout job — creates a new race condition: `TransferExpired` may arrive at wallet-service before `MoneyTransferInitiated` (different producers, different topics, no ordering guarantee). `expiresAt` embedded in the original event gives each downstream service the information it needs to self-determine expiry without any cross-service calls or additional event types.

### Why pessimistic locking with sorted UUID ordering in SettlementService?

Two concurrent transfers involving the same users (A→B and B→A) could deadlock if each locks wallets in different orders. Sorting UUIDs before acquiring locks ensures both transfers always lock in the same order, making deadlock structurally impossible.
