#!/usr/bin/env bash
set -Eeuo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DEPLOY_DIR"

if [[ ! -f .env ]]; then
  echo "Missing $DEPLOY_DIR/.env. Create it from deploy/.env.example first." >&2
  exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is not installed on this server." >&2
  exit 1
fi

export IMAGE_TAG="${IMAGE_TAG:-latest}"

COMPOSE=(docker compose --env-file .env -f docker-compose.prod.yml)

"${COMPOSE[@]}" config --quiet
"${COMPOSE[@]}" pull backend frontend
"${COMPOSE[@]}" up -d --remove-orphans --wait --wait-timeout 180

"${COMPOSE[@]}" exec -T frontend wget -q --spider http://127.0.0.1/
"${COMPOSE[@]}" exec -T frontend wget -q --spider \
  "http://backend:8080/api/v1/products?page=0&size=1"

"${COMPOSE[@]}" ps
