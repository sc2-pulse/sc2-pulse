-- Copyright (C) 2020 Oleksandr Masniuk and contributors
-- SPDX-License-Identifier: AGPL-3.0-or-later
---
CREATE TABLE "account"
(

    "id" BIGSERIAL,
    "partition" SMALLINT NOT NULL,
    "battle_tag" VARCHAR(30) NOT NULL,
    "updated" TIMESTAMP NOT NULL,

    PRIMARY KEY ("id"),

    CONSTRAINT "uq_account_partition_battle_tag"
        UNIQUE ("partition", "battle_tag")

);

CREATE INDEX "ix_account_updated" ON "account"("updated");
CREATE INDEX "ix_account_battle_tag" ON "account"(LOWER("battle_tag") text_pattern_ops);

CREATE TABLE "player_character"
(

    "id" BIGSERIAL,
    "account_id" BIGINT NOT NULL,
    "battlenet_id" BIGINT NOT NULL,
    "region" SMALLINT NOT NULL,
    "realm" SMALLINT NOT NULL,
    "name" VARCHAR(30) NOT NULL,

    PRIMARY KEY ("id"),

    CONSTRAINT "fk_player_character_account_id"
        FOREIGN KEY ("account_id")
        REFERENCES "account"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT "uq_player_character_region_battlenet_id"
        UNIQUE ("region", "battlenet_id")

);

CREATE INDEX "ix_player_character_account_id" ON "player_character"("account_id");
CREATE INDEX "ix_player_character_battlenet_id" ON "player_character"("battlenet_id");
CREATE INDEX "ix_player_character_name" ON "player_character"(LOWER("name") text_pattern_ops);

CREATE TABLE "season"
(

    "id" BIGSERIAL,
    "region" SMALLINT NOT NULL,
    "battlenet_id" SMALLINT NOT NULL,
    "year" SMALLINT NOT NULL,
    "number" SMALLINT NOT NULL,
    "start" DATE NOT NULL,
    "end" DATE NOT NULL,

    PRIMARY KEY ("id"),

    CONSTRAINT "uq_season_region_battlenet_id"
        UNIQUE ("region", "battlenet_id")
);

CREATE TABLE "league"
(

    "id" BIGSERIAL,
    "season_id" BIGINT NOT NULL,
    "type" SMALLINT NOT NULL,
    "queue_type" SMALLINT NOT NULL,
    "team_type" SMALLINT NOT NULL,

    PRIMARY KEY ("id"),

    CONSTRAINT "fk_league_season_id"
        FOREIGN KEY ("season_id")
        REFERENCES "season"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT "uq_league_season_id_type_queue_type_team_type"
        UNIQUE ("season_id", "type", "queue_type", "team_type")

);

CREATE TABLE "league_tier"
(

    "id" BIGSERIAL,
    "league_id" BIGINT NOT NULL,
    "type" SMALLINT NOT NULL,
    "min_rating" SMALLINT,
    "max_rating" SMALLINT,

    PRIMARY KEY ("id"),

    CONSTRAINT "fk_league_tier_league_id"
        FOREIGN KEY ("league_id")
        REFERENCES "league"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT "uq_league_tier_league_id_type"
        UNIQUE ("league_id", "type")

);

CREATE TABLE "division"
(

    "id" BIGSERIAL,
    "league_tier_id" BIGINT NOT NULL,
    "battlenet_id" BIGINT NOT NULL,

    PRIMARY KEY ("id"),

    CONSTRAINT "fk_division_league_tier_id"
        FOREIGN KEY ("league_tier_id")
        REFERENCES "league_tier"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT "uq_division_league_tier_id_battlenet_id"
        UNIQUE("league_tier_id", "battlenet_id")

);

CREATE TABLE "team"
(

    "id" BIGSERIAL,
    "division_id" BIGINT NOT NULL,
    "battlenet_id" NUMERIC(20, 0) NOT NULL,
    "season" SMALLINT NOT NULL,
    "region" SMALLINT NOT NULL,
    "league_type" SMALLINT NOT NULL,
    "queue_type" SMALLINT NOT NULL,
    "team_type" SMALLINT NOT NULL,
    "tier_type" SMALLINT NOT NULL,
    "rating" SMALLINT NOT NULL,
    "points" SMALLINT NOT NULL,
    "wins" SMALLINT NOT NULL,
    "losses" SMALLINT NOT NULL,
    "ties" SMALLINT NOT NULL,
    "global_rank" INTEGER NOT NULL DEFAULT 2147483647,
    "region_rank" INTEGER NOT NULL DEFAULT 2147483647,
    "league_rank" INTEGER NOT NULL DEFAULT 2147483647,

    PRIMARY KEY ("id"),

    CONSTRAINT "fk_team_division_id"
        FOREIGN KEY ("division_id")
        REFERENCES "division"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT "uq_team_region_battlenet_id"
        UNIQUE ("region", "battlenet_id")

);

CREATE INDEX "ix_team_ladder_search_full" ON "team"("season", "queue_type", "team_type", "rating", "id", "region", "league_type");

CREATE TABLE "team_member"
(
    "team_id" BIGINT NOT NULL,
    "player_character_id" BIGINT NOT NULL,
    "terran_games_played" SMALLINT,
    "protoss_games_played" SMALLINT,
    "zerg_games_played" SMALLINT,
    "random_games_played" SMALLINT,

    PRIMARY KEY ("team_id", "player_character_id"),

    CONSTRAINT "fk_team_member_team_id"
        FOREIGN KEY ("team_id")
        REFERENCES "team"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "fk_team_player_character_id"
        FOREIGN KEY ("player_character_id")
        REFERENCES "player_character"("id")
        ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE INDEX "ix_fk_team_member_player_character_id" ON "team_member"("player_character_id");

CREATE TABLE "queue_stats"
(
    "id" BIGSERIAL,
    "season" SMALLINT NOT NULL,
    "queue_type" SMALLINT NOT NULL,
    "team_type" SMALLINT NOT NULL,
    "player_base" BIGINT NOT NULL,
    "player_count" INTEGER NOT NULL,
    "low_activity_player_count" INTEGER NOT NULL DEFAULT 0,
    "medium_activity_player_count" INTEGER NOT NULL DEFAULT 0,
    "high_activity_player_count" INTEGER NOT NULL DEFAULT 0,

    PRIMARY KEY ("id"),

    CONSTRAINT "uq_queue_stats_queue_type_team_type_season"
        UNIQUE ("queue_type", "team_type", "season")
);

CREATE TABLE "league_stats"
(
    "league_id" BIGINT NOT NULL,
    "team_count" INTEGER NOT NULL,
    "terran_games_played" INTEGER NOT NULL,
    "protoss_games_played" INTEGER NOT NULL,
    "zerg_games_played" INTEGER NOT NULL,
    "random_games_played" INTEGER NOT NULL,

    PRIMARY KEY ("league_id"),

    CONSTRAINT "fk_league_stats_league_id"
        FOREIGN KEY ("league_id")
        REFERENCES "league"("id")
        ON DELETE CASCADE ON UPDATE CASCADE
);

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
    "updated" TIMESTAMP NOT NULL DEFAULT NOW(),

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

CREATE INDEX "ix_player_character_stats_calculation"
    ON "player_character_stats"("player_character_id", "race", "queue_type", "team_type", "season_id");

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
