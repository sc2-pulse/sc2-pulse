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
    DROP CONSTRAINT "fk_team_league_tier_id",
    DROP CONSTRAINT "fk_team_division_id",
    DROP CONSTRAINT "uq_team_region_battlenet_id",
    ADD COLUMN "queue_type" SMALLINT NOT NULL DEFAULT 201;
DROP INDEX "ix_team_ladder_search";

UPDATE "team"
    SET "queue_type" = "league"."queue_type"
    FROM "league_tier"
    INNER JOIN "league" ON "league_tier"."league_id" = "league"."id"
    WHERE "team"."league_tier_id" = "league_tier"."id"
    AND "league"."queue_type" != 201;

ALTER TABLE "team"
    ADD CONSTRAINT "fk_team_league_tier_id"
        FOREIGN KEY ("league_tier_id")
        REFERENCES "league_tier"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    ADD CONSTRAINT "fk_team_division_id"
        FOREIGN KEY ("division_id")
        REFERENCES "division"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    ADD CONSTRAINT "uq_team_region_battlenet_id"
        UNIQUE ("region", "battlenet_id"),
    ALTER COLUMN "queue_type" DROP DEFAULT;
CREATE INDEX "ix_team_ladder_search" ON "team"("queue_type", "rating", "id");

SET work_mem = '4MB';

VACUUM(ANALYZE);