-- Copyright (C) 2021 Oleksandr Masniuk and contributors
-- SPDX-License-Identifier: AGPL-3.0-or-later
---

ALTER SEQUENCE "season_id_seq" AS INTEGER;
ALTER TABLE "season" ALTER COLUMN "id" TYPE INTEGER;
ALTER TABLE "league" ALTER COLUMN "season_id" TYPE INTEGER;
ALTER TABLE "player_character_stats" ALTER COLUMN "season_id" TYPE INTEGER;

ALTER SEQUENCE "league_id_seq" AS INTEGER;
ALTER TABLE "league" ALTER COLUMN "id" TYPE INTEGER;
ALTER TABLE "league_tier" ALTER COLUMN "league_id" TYPE INTEGER;
ALTER TABLE "league_stats" ALTER COLUMN "league_id" TYPE INTEGER;

ALTER SEQUENCE "league_tier_id_seq" AS INTEGER;
ALTER TABLE "league_tier" ALTER COLUMN "id" TYPE INTEGER;
ALTER TABLE "division" ALTER COLUMN "league_tier_id" TYPE INTEGER;

ALTER SEQUENCE "division_id_seq" AS INTEGER;
ALTER TABLE "division" ALTER COLUMN "id" TYPE INTEGER;
ALTER TABLE "team" ALTER COLUMN "division_id" TYPE INTEGER;
ALTER TABLE "team_state" ALTER COLUMN "division_id" TYPE INTEGER;
