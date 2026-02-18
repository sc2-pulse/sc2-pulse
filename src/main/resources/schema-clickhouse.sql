-- Copyright (C) 2020-2026 Oleksandr Masniuk
-- SPDX-License-Identifier: AGPL-3.0-or-later
---

CREATE TABLE "team_state"
(
    "team_legacy_uid" String CODEC(LZ4),
    "timestamp" DateTime('UTC') CODEC(T64, ZSTD(1)),
    "division_battlenet_id" Int64 CODEC(ZSTD(1)),
    "league_type" Enum8(
        'BRONZE' = 0,
        'SILVER' = 1,
        'GOLD' = 2,
        'PLATINUM' = 3,
        'DIAMOND' = 4,
        'MASTER' = 5,
        'GRANDMASTER' = 6
    ) CODEC(LZ4),
    "tier_type" Enum8(
        'FIRST' = 0,
        'SECOND' = 1,
        'THIRD' = 2
    ) CODEC(LZ4),
    "season" Int16 CODEC(Delta, LZ4),
    "wins" Int16 DEFAULT -1 CODEC(Delta, ZSTD(1)),
    "games" Int16 CODEC(Delta, ZSTD(1)),
    "games_delta" Int16 CODEC(ZSTD(1)),
    "rating" Int16 CODEC(Delta, ZSTD(1)),
    "global_rank" Int32 DEFAULT -1 CODEC(T64, ZSTD(1)),
    "region_rank" Int32 DEFAULT -1 CODEC(T64, ZSTD(1)),
    "league_rank" Int32 DEFAULT -1 CODEC(T64, ZSTD(1)),
    "global_team_count" Int32 DEFAULT -1 CODEC(T64, ZSTD(1)),
    "region_team_count" Int32 DEFAULT -1 CODEC(T64, ZSTD(1)),
    "league_team_count" Int32 DEFAULT -1 CODEC(T64, ZSTD(1)),
    "source" Enum8(
        'SYSTEM' = 0,
        'USER' = 1
    ) CODEC(LZ4),

    PROJECTION p_timestamp_max (SELECT MAX(timestamp))
)
ENGINE = MergeTree
ORDER BY ("team_legacy_uid", "timestamp");
