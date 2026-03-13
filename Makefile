include .env
export

PROJECT_NAME = money-transfer-platform
INFRA_FILE   = -f docker/infra.yml
DEV_FILE     = -f docker/dev-tools.yml
ENV_FILE     = --env-file .env

# ─── Infrastructure ───────────────────────────────────────────
up-infra:
	docker compose -p $(PROJECT_NAME) $(ENV_FILE) $(INFRA_FILE) up -d

# ─── Dev Tools ────────────────────────────────────────────────
up-dev-tools:
	docker compose -p $(PROJECT_NAME) $(ENV_FILE) $(INFRA_FILE) $(DEV_FILE) up -d

restart-dev-tool: ## Usage: make restart-dev-tool SERVICE=grafana
	docker compose -p $(PROJECT_NAME) $(ENV_FILE) $(INFRA_FILE) $(DEV_FILE) up -d $(SERVICE)

# ─── Down ─────────────────────────────────────────────────────
down:
	docker compose -p $(PROJECT_NAME) $(ENV_FILE) $(INFRA_FILE) $(DEV_FILE) down --remove-orphans
