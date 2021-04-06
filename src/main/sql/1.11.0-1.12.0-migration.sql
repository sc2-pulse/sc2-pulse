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
    ADD COLUMN "tier_type" SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN "legacy_id" NUMERIC;
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

UPDATE "team"
    SET "legacy_id" = CONCAT("player_character"."realm", "player_character"."battlenet_id", 1)::numeric
    FROM "team_member"
    INNER JOIN "player_character" ON "team_member"."player_character_id" = "player_character"."id"
    WHERE "team"."id" = "team_member"."team_id"
    AND "team"."queue_type" = 201
    AND COALESCE("team_member"."terran_games_played", 0) > COALESCE("team_member"."protoss_games_played", 0)
    AND COALESCE("team_member"."terran_games_played", 0) > COALESCE("team_member"."zerg_games_played", 0)
    AND COALESCE("team_member"."terran_games_played", 0) > COALESCE("team_member"."random_games_played", 0);
UPDATE "team"
    SET "legacy_id" = CONCAT("player_character"."realm", "player_character"."battlenet_id", 2)::numeric
    FROM "team_member"
    INNER JOIN "player_character" ON "team_member"."player_character_id" = "player_character"."id"
    WHERE "team"."id" = "team_member"."team_id"
    AND "team"."queue_type" = 201
    AND COALESCE("team_member"."protoss_games_played", 0) > COALESCE("team_member"."terran_games_played", 0)
    AND COALESCE("team_member"."protoss_games_played", 0) > COALESCE("team_member"."zerg_games_played", 0)
    AND COALESCE("team_member"."protoss_games_played", 0) > COALESCE("team_member"."random_games_played", 0);
UPDATE "team"
    SET "legacy_id" = CONCAT("player_character"."realm", "player_character"."battlenet_id", 3)::numeric
    FROM "team_member"
    INNER JOIN "player_character" ON "team_member"."player_character_id" = "player_character"."id"
    WHERE "team"."id" = "team_member"."team_id"
    AND "team"."queue_type" = 201
    AND COALESCE("team_member"."zerg_games_played", 0) > COALESCE("team_member"."protoss_games_played", 0)
    AND COALESCE("team_member"."zerg_games_played", 0) > COALESCE("team_member"."terran_games_played", 0)
    AND COALESCE("team_member"."zerg_games_played", 0) > COALESCE("team_member"."random_games_played", 0);
UPDATE "team"
    SET "legacy_id" = CONCAT("player_character"."realm", "player_character"."battlenet_id", 4)::numeric
    FROM "team_member"
    INNER JOIN "player_character" ON "team_member"."player_character_id" = "player_character"."id"
    WHERE "team"."id" = "team_member"."team_id"
    AND "team"."queue_type" = 201
    AND COALESCE("team_member"."random_games_played", 0) > COALESCE("team_member"."protoss_games_played", 0)
    AND COALESCE("team_member"."random_games_played", 0) > COALESCE("team_member"."zerg_games_played", 0)
    AND COALESCE("team_member"."random_games_played", 0) > COALESCE("team_member"."terran_games_played", 0);

CREATE INDEX "ix_team_legacy_id_duplicate" ON "team"("season", "region", "queue_type", "legacy_id");
DELETE FROM "team"
USING "team" team2
WHERE "team"."season" = "team2"."season"
AND "team"."region" = "team2"."region"
AND "team"."queue_type" = "team2"."queue_type"
AND "team"."legacy_id" = "team2"."legacy_id"
AND "team"."queue_type" = 201
AND "team"."wins" + "team"."losses" + "team"."ties" < "team2"."wins" + "team2"."losses" + "team2"."ties";
DELETE FROM "team"
USING "team" team2
WHERE "team"."season" = "team2"."season"
AND "team"."region" = "team2"."region"
AND "team"."queue_type" = "team2"."queue_type"
AND "team"."legacy_id" = "team2"."legacy_id"
AND "team"."queue_type" = 201
AND "team"."id" < "team2"."id";
DROP INDEX "ix_team_legacy_id_duplicate";

ALTER TABLE "team"
    ADD CONSTRAINT "fk_team_division_id"
        FOREIGN KEY ("division_id")
        REFERENCES "division"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    ADD CONSTRAINT "uq_team_region_battlenet_id"
        UNIQUE ("region", "battlenet_id"),
    ADD CONSTRAINT "uq_team_season_region_queue_type_legacy_id"
        UNIQUE ("season", "region", "queue_type", "legacy_id"),
    ALTER COLUMN "season" DROP DEFAULT,
    ALTER COLUMN "queue_type" DROP DEFAULT,
    ALTER COLUMN "team_type" DROP DEFAULT,
    ALTER COLUMN "league_type" DROP DEFAULT,
    ALTER COLUMN "tier_type" DROP DEFAULT;
CREATE INDEX "ix_team_ladder_search_full" ON "team"("season", "queue_type", "team_type", "rating", "id");

SET work_mem = '4MB';

VACUUM(ANALYZE);

