include .env
export

PROJECT_NAME = money-transfer-platform
INFRA_FILE   = -f docker/infra.yml
DEV_FILE     = -f docker/dev-tools.yml
APP_FILE     = -f docker/app.yml
ENV_FILE     = --env-file .env

# ─── Infrastructure ───────────────────────────────────────────
up-infra:
	docker compose -p $(PROJECT_NAME) $(ENV_FILE) $(INFRA_FILE) up -d

# ─── Dev Tools ────────────────────────────────────────────────
up-dev-tools:
	docker compose -p $(PROJECT_NAME) $(ENV_FILE) $(INFRA_FILE) $(DEV_FILE) up -d

restart-dev-tool: # Usage: make restart-dev-tool SERVICE=grafana
	docker compose -p $(PROJECT_NAME) $(ENV_FILE) $(INFRA_FILE) $(DEV_FILE) up -d $(SERVICE)

# ─── Full Stack ───────────────────────────────────────────────
up-dev:
	docker compose -p $(PROJECT_NAME) $(ENV_FILE) $(INFRA_FILE) $(APP_FILE) $(DEV_FILE) up -d --remove-orphans

# ─── Rebuild & Start ──────────────────────────────────────────
rebuild-service:
	./mvnw clean install -pl $(MODULE) -am -DskipTests
	docker compose -p $(PROJECT_NAME) $(ENV_FILE) $(INFRA_FILE) $(APP_FILE) up -d --build $(SERVICE)

# ─── Schema Registry ──────────────────────────────────────────
register-schemas:
	./mvnw -pl common/transfer-api-contract \
		-P register-schemas \
		io.confluent:kafka-schema-registry-maven-plugin:$(confluent.version):register \
		-Dschema.registry.url=$(SC_REGISTRY_HOST_URL)

# ─── Down ─────────────────────────────────────────────────────
down:
	docker compose -p $(PROJECT_NAME) $(ENV_FILE) $(INFRA_FILE) $(DEV_FILE) down --remove-orphans
