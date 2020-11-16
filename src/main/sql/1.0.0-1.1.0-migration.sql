-- Copyright (C) 2020 Oleksandr Masniuk and contributors
-- SPDX-License-Identifier: AGPL-3.0-or-later
---

DROP INDEX "ix_player_character_name";
CREATE INDEX "ix_player_character_name" ON "player_character"(LOWER("name") text_pattern_ops);

CREATE TABLE "player_character_stats"
(
    "id" BIGSERIAL,
    "player_character_id" BIGINT NOT NULL,
    "season_id" BIGINT,
    "queue_type" SMALLINT NOT NULL,
    "team_type" SMALLINT NOT NULL,
    "race" SMALLINT,
    "rating_max" SMALLINT NOT NULL,
    "league_max" SMALLINT NOT NULL,
    "games_played" INTEGER NOT NULL,

    PRIMARY KEY ("id"),

    CONSTRAINT "fk_player_character_stats_player_character_id"
        FOREIGN KEY ("player_character_id")
        REFERENCES "player_character"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "fk_player_character_stats_season_id"
        FOREIGN KEY ("season_id")
        REFERENCES "season"("id")
        ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE UNIQUE INDEX "uq_player_character_stats_main"
    ON "player_character_stats"("player_character_id", COALESCE("season_id", -32768), COALESCE("race", -32768), "queue_type", "team_type");

ALTER TABLE "player_character" ADD COLUMN "region" SMALLINT,
DROP CONSTRAINT "uq_player_character_account_id_battlenet_id";

UPDATE "player_character" SET "region"="account"."region"
FROM "account" WHERE "player_character"."account_id"="account"."id";

ALTER TABLE "player_character" ALTER COLUMN "region" SET NOT NULL,
ADD CONSTRAINT "uq_player_character_region_battlenet_id"
UNIQUE ("region", "battlenet_id");

ALTER TABLE "player_character"
ADD COLUMN "battle_tag" VARCHAR(30),
DROP CONSTRAINT "fk_player_character_account_id";

UPDATE "player_character" SET "battle_tag"="account"."battle_tag"
FROM "account" WHERE "player_character"."account_id"="account"."id";

DELETE FROM "account"
USING
(
   SELECT "battle_tag", MIN("id") AS min_id
   FROM "account"
   GROUP BY "battle_tag"
) sub
WHERE "account"."battle_tag"=sub."battle_tag"
AND "account"."id" != sub.min_id;

UPDATE "player_character" SET "account_id"="account"."id"
FROM "account" WHERE "player_character"."battle_tag"="account"."battle_tag";

ALTER TABLE "player_character"
DROP COLUMN "battle_tag",
ADD CONSTRAINT "fk_player_character_account_id"
    FOREIGN KEY ("account_id")
    REFERENCES "account"("id")
    ON DELETE CASCADE ON UPDATE CASCADE;

DROP INDEX "ix_account_battle_tag";
ALTER TABLE "account"
DROP CONSTRAINT "uq_account_region_battlenet_id",
DROP COLUMN "region",
DROP COLUMN "battlenet_id";
ALTER TABLE "account" ADD CONSTRAINT "uq_account_battle_tag" UNIQUE("battle_tag");

CREATE INDEX "ix_player_character_account_id" ON "player_character"("account_id");

CREATE TABLE "account_following"
(
    "account_id" BIGINT NOT NULL,
    "following_account_id" BIGINT NOT NULL,

    PRIMARY KEY ("account_id", "following_account_id"),

    CONSTRAINT "fk_account_following_account_id"
        FOREIGN KEY ("account_id")
        REFERENCES "account"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "fk_account_following_following_account_id"
        FOREIGN KEY ("following_account_id")
        REFERENCES "account"("id")
        ON DELETE CASCADE ON UPDATE CASCADE
);
