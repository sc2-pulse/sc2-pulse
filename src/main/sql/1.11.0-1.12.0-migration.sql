-- Copyright (C) 2021 Oleksandr Masniuk and contributors
-- SPDX-License-Identifier: AGPL-3.0-or-later
---

VACUUM(ANALYZE);
SET work_mem = '128MB';

ALTER TABLE "account" ALTER COLUMN "battle_tag" TYPE TEXT;
ALTER TABLE "player_character" ALTER COLUMN "name" TYPE TEXT;
ALTER TABLE "pro_player"
    ALTER COLUMN "nickname" TYPE TEXT,
    ALTER COLUMN "name" TYPE TEXT,
    ALTER COLUMN "team" TYPE TEXT;
ALTER TABLE "social_media_link" ALTER COLUMN "url" TYPE TEXT;
ALTER TABLE "pro_team"
    ALTER COLUMN "name" TYPE TEXT,
    ALTER COLUMN "short_name" TYPE TEXT;
ALTER TABLE "match" ALTER COLUMN "map" TYPE TEXT;

ALTER TABLE "team"
    DROP COLUMN "league_tier_id",
    DROP CONSTRAINT "fk_team_division_id",
    DROP CONSTRAINT "uq_team_region_battlenet_id",
    ADD COLUMN "season" SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN "queue_type" SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN "team_type" SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN "league_type" SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN "tier_type" SMALLINT NOT NULL DEFAULT 0;
DROP INDEX "ix_team_ladder_search";

UPDATE "team"
    SET "season" = "season"."battlenet_id",
     "queue_type" = "league"."queue_type",
     "league_type" = "league"."type",
     "team_type" = "league"."team_type",
     "tier_type" = "league_tier"."type"
    FROM "division"
    INNER JOIN "league_tier" ON "division"."league_tier_id" = "league_tier"."id"
    INNER JOIN "league" ON "league_tier"."league_id" = "league"."id"
    INNER JOIN "season" ON "league"."season_id" = "season"."id"
    WHERE "team"."division_id" = "division"."id";

ALTER TABLE "team"
    ADD CONSTRAINT "fk_team_division_id"
        FOREIGN KEY ("division_id")
        REFERENCES "division"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    ADD CONSTRAINT "uq_team_region_battlenet_id"
        UNIQUE ("region", "battlenet_id"),
    ALTER COLUMN "season" DROP DEFAULT,
    ALTER COLUMN "queue_type" DROP DEFAULT,
    ALTER COLUMN "team_type" DROP DEFAULT,
    ALTER COLUMN "league_type" DROP DEFAULT,
    ALTER COLUMN "tier_type" DROP DEFAULT;
CREATE INDEX "ix_team_ladder_search_full" ON "team"("season", "queue_type", "team_type", "rating", "id");

SET work_mem = '4MB';

VACUUM(ANALYZE);

