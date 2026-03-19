include .env
export

# ══════════════════════════════════════════════════════════════════
#  Money Transfer Platform — Makefile
# ══════════════════════════════════════════════════════════════════

PROJECT_NAME := money-transfer-platform

INFRA_FILE   := -f docker/infra.yml
APP_FILE     := -f docker/app.yml
DEV_FILE     := -f docker/dev-tools.yml
TOOLS_FILE   := -f docker/tools.yml
ENV_FILE     := --env-file .env

COMPOSE      := docker compose -p $(PROJECT_NAME) $(ENV_FILE)

.DEFAULT_GOAL := help

# ── Help ──────────────────────────────────────────────────────────
.PHONY: help
help:
	@echo ""
	@echo "  Money Transfer Platform"
	@echo ""
	@echo "  Infrastructure"
	@echo "    up-infra              Start PostgreSQL, Kafka, Schema Registry, Kafka Connect, Keycloak, Redis"
	@echo "    down                  Stop and remove all containers"
	@echo ""
	@echo "  Kafka"
	@echo "    topics-apply          Create Kafka topics via Terraform"
	@echo "    register-schemas      Register Protobuf schemas to Schema Registry"
	@echo "    register-connectors   Register Debezium connectors to Kafka Connect"
	@echo ""
	@echo "  Application"
	@echo "    up-app                Start all services"
	@echo "    up-dev                Start full stack (infra + app + dev tools)"
	@echo "    up-dev-tools          Start dev tools (Kafka UI, pgAdmin)"
	@echo ""
	@echo "  Build"
	@echo "    rebuild-service       Rebuild and restart a single service"
	@echo "                          Usage: make rebuild-service MODULE=services/transaction-service SERVICE=transaction"
	@echo ""

# ══════════════════════════════════════════════════════════════════
#  INFRASTRUCTURE
# ══════════════════════════════════════════════════════════════════

.PHONY: up-infra
up-infra: # Start core infrastructure
	$(COMPOSE) $(INFRA_FILE) up -d
	@echo "✓ Infrastructure started"

.PHONY: down
down: # Stop and remove all containers
	$(COMPOSE) $(INFRA_FILE) $(APP_FILE) $(DEV_FILE) down --remove-orphans
	@echo "✓ All containers stopped"

# ══════════════════════════════════════════════════════════════════
#  KAFKA
#  Run in order: topics-apply → register-schemas → register-connectors
# ══════════════════════════════════════════════════════════════════

.PHONY: topics-apply
topics-apply: # Create Kafka topics via Terraform (requires Kafka running)
	@echo "→ Initializing Terraform..."
	$(COMPOSE) $(TOOLS_FILE) run --rm terraform init
	@echo "→ Applying Kafka topics..."
	$(COMPOSE) $(TOOLS_FILE) run --rm terraform apply -auto-approve
	@echo "✓ Kafka topics created"

.PHONY: register-schemas
register-schemas: # Register Protobuf schemas to Schema Registry (requires Schema Registry running)
	./mvnw -pl common/transfer-api-contract \
		-P register-schemas \
		io.confluent:kafka-schema-registry-maven-plugin:$(CONFLUENT_VERSION):register \
		-Dschema.registry.url=$(SC_REGISTRY_HOST_URL)
	@echo "✓ Schemas registered"

.PHONY: register-connectors
register-connectors: # Register Debezium connectors to Kafka Connect (requires topics created)
	@echo "→ Registering Debezium connectors..."
	@bash docker/connectors/register.sh
	@echo "✓ Connectors registered"

# ══════════════════════════════════════════════════════════════════
#  APPLICATION
# ══════════════════════════════════════════════════════════════════

.PHONY: up-app
up-app: # Start all services
	$(COMPOSE) $(INFRA_FILE) $(APP_FILE) up -d --remove-orphans
	@echo "✓ Services started"

.PHONY: up-dev-tools
up-dev-tools: # Start observability stack
	$(COMPOSE) $(INFRA_FILE) $(DEV_FILE) up -d
	@echo "✓ Dev tools started"

.PHONY: up-dev
up-dev: # Start full stack — infra + app + dev tools
	$(COMPOSE) $(INFRA_FILE) $(APP_FILE) $(DEV_FILE) up -d --remove-orphans
	@echo "✓ Full stack started"

# ══════════════════════════════════════════════════════════════════
#  BUILD
# ══════════════════════════════════════════════════════════════════

# Usage: make rebuild-service MODULE=services/transaction-service SERVICE=transaction-service
.PHONY: rebuild-service
rebuild-service:
	@echo "→ Building $(MODULE)..."
	./mvnw clean install -pl $(MODULE) -am -DskipTests
	@echo "→ Restarting $(SERVICE)..."
	$(COMPOSE) $(INFRA_FILE) $(APP_FILE) up -d --build $(SERVICE)
	@echo "✓ $(SERVICE) rebuilt and restarted"