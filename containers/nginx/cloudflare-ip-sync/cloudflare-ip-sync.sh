#!/usr/bin/env bash
# Copyright (C) 2020-2026 Oleksandr Masniuk
# SPDX-License-Identifier: AGPL-3.0-or-later
set -euo pipefail

###############################################################################
# CONFIGURATION
###############################################################################

NGINX_IP_FILE="conf/cloudflare-ips.conf"
NGINX_CONTAINER_NAME="sc2pulse-nginx-prod"

HTTP_PORT="80"
HTTPS_PORT="443"

CF_V4_URL="https://www.cloudflare.com/ips-v4"
CF_V6_URL="https://www.cloudflare.com/ips-v6"

NFT_TABLE="cloudflare"
NFT_SET_V4="cf_ipv4"
NFT_SET_V6="cf_ipv6"

LOGGER_TAG="cloudflare-ip-sync"

###############################################################################
# FUNCTIONS
###############################################################################

log() {
  logger -t "$LOGGER_TAG" "$1"
}

fatal() {
  log "ERROR: $1"
  exit 1
}

validate_cidr_file() {
  local file="$1"
  grep -Eq '^[0-9a-fA-F:.]+/[0-9]+$' "$file" \
    || fatal "CIDR validation failed for $file"
}

###############################################################################
# PREP
###############################################################################

umask 077
tmpdir="$(mktemp -d)"
trap 'rm -rf "$tmpdir"' EXIT

log "Starting Cloudflare IP sync (Docker-aware)"

###############################################################################
# FETCH
###############################################################################

curl -fsSL "$CF_V4_URL" -o "$tmpdir/cf-v4.txt" \
  || fatal "Failed to fetch Cloudflare IPv4 list"

curl -fsSL "$CF_V6_URL" -o "$tmpdir/cf-v6.txt" \
  || fatal "Failed to fetch Cloudflare IPv6 list"

###############################################################################
# VALIDATION
###############################################################################

[ -s "$tmpdir/cf-v4.txt" ] || fatal "IPv4 list empty"
[ -s "$tmpdir/cf-v6.txt" ] || fatal "IPv6 list empty"

validate_cidr_file "$tmpdir/cf-v4.txt"
validate_cidr_file "$tmpdir/cf-v6.txt"

log "Cloudflare IP lists validated"

###############################################################################
# NGINX CONFIG GENERATION
###############################################################################

{
  echo "# AUTO-GENERATED — DO NOT EDIT"
  echo "# Generated: $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  echo ""

  awk '{ print "set_real_ip_from " $0 ";" }' "$tmpdir/cf-v4.txt"
  awk '{ print "set_real_ip_from " $0 ";" }' "$tmpdir/cf-v6.txt"

  echo ""
  echo "real_ip_header CF-Connecting-IP;"
} > "$tmpdir/nginx-cloudflare.conf"

# Atomic update and reload
cp "$tmpdir/nginx-cloudflare.conf" "$NGINX_IP_FILE"
if [ "$(docker ps -q -f name=^/"${NGINX_CONTAINER_NAME}"$ -f status=running)" ]; then
    docker exec "$NGINX_CONTAINER_NAME" sh -c 'nginx -t && nginx -s reload' || \
      fatal "NGINX reload failed - check config"
else
    log "NGINX container is not running"
fi

log "NGINX Trusted IPs updated"

###############################################################################
# NFTABLES (DOCKER-AWARE, ATOMIC)
###############################################################################

NFT_TMP="${tmpdir}/cloudflare.nft"

{
  echo "table inet ${NFT_TABLE} {}"
  echo "flush table inet ${NFT_TABLE}"
  echo "table inet ${NFT_TABLE} {"

  echo "  set ${NFT_SET_V4} {"
  echo "    type ipv4_addr;"
  echo "    flags interval;"
  echo "    elements = {"
  sed 's/$/,/' "$tmpdir/cf-v4.txt"
  echo "    }"
  echo "  }"

  echo "  set ${NFT_SET_V6} {"
  echo "    type ipv6_addr;"
  echo "    flags interval;"
  echo "    elements = {"
  sed 's/$/,/' "$tmpdir/cf-v6.txt"
  echo "    }"
  echo "  }"

  echo "  chain docker_user {"
  echo "    type filter hook forward priority -200; policy accept;"
  echo "    ct state established,related accept"

  echo "    ct status dnat tcp dport ${HTTPS_PORT} ip saddr @${NFT_SET_V4} accept"
  echo "    ct status dnat tcp dport ${HTTPS_PORT} ip6 saddr @${NFT_SET_V6} accept"
  echo "    ct status dnat tcp dport ${HTTPS_PORT} log prefix \"CF DROP HTTPS \" drop"

  echo "    ct status dnat tcp dport ${HTTP_PORT} ip saddr @${NFT_SET_V4} accept"
  echo "    ct status dnat tcp dport ${HTTP_PORT} ip6 saddr @${NFT_SET_V6} accept"
  echo "    ct status dnat tcp dport ${HTTP_PORT} log prefix \"CF DROP HTTP \" drop"

  echo "  }"

  echo "}"
} > "$NFT_TMP"

nft -f "$NFT_TMP" || fatal "Failed to apply nftables rules"

log "nftables Docker-forward rules updated"
log "Cloudflare IP sync completed successfully"
