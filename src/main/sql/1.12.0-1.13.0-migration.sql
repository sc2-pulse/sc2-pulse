-- Copyright (C) 2021 Oleksandr Masniuk and contributors
-- SPDX-License-Identifier: AGPL-3.0-or-later
---

VACUUM(ANALYZE);
SET work_mem = '128MB';

ALTER TABLE "team"
    DROP CONSTRAINT "fk_team_division_id",
    DROP CONSTRAINT "uq_team_region_battlenet_id",
    DROP CONSTRAINT "uq_team_season_region_queue_type_legacy_id";
DROP INDEX "ix_team_ladder_search_full";

UPDATE "team"
    SET "legacy_id" = "team_filter"."legacy_id"::numeric
    FROM (
        SELECT
        "team"."id",
        string_agg("player_character"."realm"::text || "player_character"."battlenet_id"::text,
            '' ORDER BY "player_character"."realm", "player_character"."battlenet_id") AS "legacy_id"
        FROM "team"
        INNER JOIN "team_member" ON "team"."id" = "team_member"."team_id"
        INNER JOIN "player_character" ON "team_member"."player_character_id" = "player_character"."id"
        WHERE "team"."queue_type" != 201
        GROUP BY "team"."id"
    ) "team_filter"
    WHERE "team"."id" = "team_filter"."id";

UPDATE "team"
    SET "legacy_id" = CONCAT("player_character"."realm", "player_character"."battlenet_id", 1)::numeric
    FROM "team_member"
    INNER JOIN "player_character" ON "team_member"."player_character_id" = "player_character"."id"
    WHERE "team"."id" = "team_member"."team_id"
    AND "team"."queue_type" = 201
    AND "team"."legacy_id" IS NULL
    AND COALESCE("team_member"."terran_games_played", 0) > 0;
UPDATE "team"
    SET "legacy_id" = CONCAT("player_character"."realm", "player_character"."battlenet_id", 2)::numeric
    FROM "team_member"
    INNER JOIN "player_character" ON "team_member"."player_character_id" = "player_character"."id"
    WHERE "team"."id" = "team_member"."team_id"
    AND "team"."queue_type" = 201
    AND "team"."legacy_id" IS NULL
    AND COALESCE("team_member"."protoss_games_played", 0) > 0;
UPDATE "team"
    SET "legacy_id" = CONCAT("player_character"."realm", "player_character"."battlenet_id", 3)::numeric
    FROM "team_member"
    INNER JOIN "player_character" ON "team_member"."player_character_id" = "player_character"."id"
    WHERE "team"."id" = "team_member"."team_id"
    AND "team"."queue_type" = 201
    AND "team"."legacy_id" IS NULL
    AND COALESCE("team_member"."zerg_games_played", 0) > 0;
UPDATE "team"
    SET "legacy_id" = CONCAT("player_character"."realm", "player_character"."battlenet_id", 4)::numeric
    FROM "team_member"
    INNER JOIN "player_character" ON "team_member"."player_character_id" = "player_character"."id"
    WHERE "team"."id" = "team_member"."team_id"
    AND "team"."queue_type" = 201
    AND "team"."legacy_id" IS NULL
    AND COALESCE("team_member"."random_games_played", 0) > 0;

CREATE INDEX "ix_team_legacy_id_duplicate" ON "team"("season", "region", "queue_type", "legacy_id");
DELETE FROM "team"
USING "team" team2
WHERE "team"."season" = "team2"."season"
AND "team"."region" = "team2"."region"
AND "team"."queue_type" = "team2"."queue_type"
AND "team"."legacy_id" = "team2"."legacy_id"
AND "team"."wins" + "team"."losses" + "team"."ties" < "team2"."wins" + "team2"."losses" + "team2"."ties";
DELETE FROM "team"
USING "team" team2
WHERE "team"."season" = "team2"."season"
AND "team"."region" = "team2"."region"
AND "team"."queue_type" = "team2"."queue_type"
AND "team"."legacy_id" = "team2"."legacy_id"
AND "team"."id" < "team2"."id";
DELETE FROM "team" WHERE "legacy_id" IS NULL;
DROP INDEX "ix_team_legacy_id_duplicate";

ALTER TABLE "team"
    ADD CONSTRAINT "fk_team_division_id"
        FOREIGN KEY ("division_id")
        REFERENCES "division"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    ADD CONSTRAINT "uq_team_region_battlenet_id"
        UNIQUE ("region", "battlenet_id"),
    ADD CONSTRAINT "uq_team_season_region_queue_type_legacy_id"
        UNIQUE ("season", "region", "queue_type", "legacy_id");
CREATE INDEX "ix_team_ladder_search_full" ON "team"("season", "queue_type", "team_type", "rating", "id");

ALTER TABLE "team"
    ALTER COLUMN "legacy_id" SET NOT NULL;

ALTER TABLE "team"
    DROP CONSTRAINT "uq_team_region_battlenet_id",
    DROP COLUMN "battlenet_id";

CREATE TABLE "var"
(
    "key" TEXT NOT NULL,
    "value" TEXT,

    PRIMARY KEY ("key")
);

SET work_mem = '4MB';
VACUUM(ANALYZE);
