#!/bin/bash
# Copyright (C) 2020-2026 Oleksandr Masniuk
# SPDX-License-Identifier: AGPL-3.0-or-later
set -euo pipefail

REQUIRED_CONTAINERS=("sc2pulse-postgresql-prod" "sc2pulse-clickhouse-prod" "sc2pulse-prod" "sc2pulse-nginx-prod")
HEALTHY_NAMES=$(docker ps --filter "health=healthy" --format "{{.Names}}")

declare -A healthy_map
for name in $HEALTHY_NAMES; do
    healthy_map["$name"]=1
done

MISSING_COUNT=0
for required in "${REQUIRED_CONTAINERS[@]}"; do
    if [[ -z "${healthy_map[$required]}" ]]; then
        echo "CRITICAL: $required is not healthy or not running."
        MISSING_COUNT=$((MISSING_COUNT + 1))
    fi
done

if [ "$MISSING_COUNT" -gt 0 ]; then
    echo "Total missing: $MISSING_COUNT. Restarting the service..."
    # java stops with non-zero code which restarts the service, offloading restart logic to systemctl
    docker stop sc2pulse-prod
    exit 1
else
    echo "All required containers are healthy."
    exit 0
fi
