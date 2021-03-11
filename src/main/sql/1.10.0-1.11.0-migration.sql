-- Copyright (C) 2021 Oleksandr Masniuk and contributors
-- SPDX-License-Identifier: AGPL-3.0-or-later
---

VACUUM;
ANALYZE;
SET work_mem = '128MB';

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

ALTER TABLE "team" ADD COLUMN "league_tier_id" INTEGER NOT NULL DEFAULT 0;

VACUUM;
ANALYZE;
UPDATE "team"
SET "league_tier_id" = "division"."league_tier_id"
FROM "division"
WHERE "team"."division_id" = "division"."id";

ALTER TABLE "team"
    ALTER COLUMN "league_tier_id" DROP DEFAULT,
    ADD CONSTRAINT "fk_team_league_tier_id"
        FOREIGN KEY ("league_tier_id")
        REFERENCES "league_tier"("id")
        ON DELETE CASCADE ON UPDATE CASCADE;

DROP INDEX "ix_team_ladder_search_full";
CREATE INDEX "ix_team_ladder_search" ON "team"("rating", "id");

ALTER TABLE "team"
    DROP COLUMN "season",
    DROP COLUMN "league_type",
    DROP COLUMN "queue_type",
    DROP COLUMN "team_type",
    DROP COLUMN "tier_type";

SET work_mem = '4MB';
