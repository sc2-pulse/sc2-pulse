-- Copyright (C) 2021 Oleksandr Masniuk and contributors
-- SPDX-License-Identifier: AGPL-3.0-or-later
---

VACUUM(ANALYZE);
SET work_mem = '128MB';

DROP INDEX "ix_player_character_stats_calculation";
DELETE FROM "player_character_stats" WHERE "season_id" IS NOT NULL;
ALTER TABLE "player_character_stats" DROP COLUMN "season_id";
CREATE UNIQUE INDEX "uq_player_character_stats_main"
    ON "player_character_stats"("player_character_id", COALESCE("race", -32768), "queue_type", "team_type");
VACUUM(FULL) "player_character_stats";

ALTER SEQUENCE "season_id_seq" AS INTEGER;
ALTER TABLE "season" ALTER COLUMN "id" TYPE INTEGER;
ALTER TABLE "league" ALTER COLUMN "season_id" TYPE INTEGER;

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

VACUUM(ANALYZE);

ALTER TABLE "team" DROP CONSTRAINT "uq_team_region_battlenet_id";
DROP INDEX "ix_team_ladder_search_full";
ALTER TABLE "team"
    DROP COLUMN "season",
    DROP COLUMN "league_type",
    DROP COLUMN "queue_type",
    DROP COLUMN "team_type",
    DROP COLUMN "tier_type";
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

ALTER TABLE "team" ADD CONSTRAINT "uq_team_region_battlenet_id" UNIQUE ("region", "battlenet_id");
CREATE INDEX "ix_team_ladder_search" ON "team"("rating", "id");
VACUUM(FULL) "team";

CREATE INDEX "ix_team_state_timestamp" ON "team_state"("timestamp");

SET work_mem = '4MB';
