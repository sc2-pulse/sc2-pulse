#!/bin/sh
# Copyright (C) 2020-2026 Oleksandr Masniuk
# SPDX-License-Identifier: AGPL-3.0-or-later

set -e

ALL_SECRETS=/run/all-secrets.txt

infisical agent --config /etc/sc2pulse/agent-config.yaml
awk -F= -v outdir="./run/sc2pulse" '
BEGIN { system("mkdir -p " outdir) }
{
    if ($1 != "") {
        print $2 > (outdir "/" $1)
    }
}' "$ALL_SECRETS"
[ -f "/run/sc2pulse/spring.datasource.password" ] && \
  cp "/run/sc2pulse/spring.datasource.password" "/run/postgres/spring.datasource.password"
rm "$ALL_SECRETS"
