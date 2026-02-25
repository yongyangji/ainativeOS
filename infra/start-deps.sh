#!/usr/bin/env bash
set -euo pipefail

docker compose -f infra/docker-compose.yml up -d
echo "MySQL is starting. Check with: docker ps"
