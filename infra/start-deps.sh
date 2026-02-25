#!/usr/bin/env bash
set -euo pipefail

docker compose -f infra/docker-compose.yml up -d --build
echo "Services are starting. Check with: docker compose -f infra/docker-compose.yml ps"
