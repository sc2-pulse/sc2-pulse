#!/bin/sh
# Copyright (C) 2020-2026 Oleksandr Masniuk
# SPDX-License-Identifier: AGPL-3.0-or-later

set -e

TARGET_DIR="/run/sc2pulse"
APP_USER="sc2pulse"

mkdir -p "$TARGET_DIR"
chown "$APP_USER":"$APP_USER" "$TARGET_DIR"
chmod 500 "$TARGET_DIR"

if [ -d "/run/secrets" ]; then
    for secret_file in /run/secrets/*; do
        if [ -f "$secret_file" ]; then
            filename=$(basename "$secret_file")
            cp "$secret_file" "$TARGET_DIR/$filename"
            chown "$APP_USER":"$APP_USER" "$TARGET_DIR/$filename"
            chmod 400 "$TARGET_DIR/$filename"
        fi
    done
fi

exec setpriv --reuid=$APP_USER --regid=$APP_USER --clear-groups -- "$@"
