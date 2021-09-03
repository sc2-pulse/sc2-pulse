-- Copyright (C) 2020 Oleksandr Masniuk and contributors
-- SPDX-License-Identifier: AGPL-3.0-or-later
---

CREATE TABLE "clan"
(
    "id" SERIAL,
    "tag" TEXT NOT NULL,
    "region" SMALLINT NOT NULL,
    "name" TEXT,

    PRIMARY KEY("id"),

    CONSTRAINT "uq_clan_tag_region"
        UNIQUE ("tag", "region")
);

CREATE TABLE "account"
(

    "id" BIGSERIAL,
    "partition" SMALLINT NOT NULL,
    "battle_tag" TEXT NOT NULL,

    PRIMARY KEY ("id"),

    CONSTRAINT "uq_account_partition_battle_tag"
        UNIQUE ("partition", "battle_tag")

);

CREATE INDEX "ix_account_battle_tag" ON "account"(LOWER("battle_tag") text_pattern_ops);

CREATE TABLE "account_role"
(
    "account_id" BIGINT NOT NULL,
    "role" SMALLINT NOT NULL,

    PRIMARY KEY ("account_id", "role")
);

CREATE TABLE "player_character"
(

    "id" BIGSERIAL,
    "account_id" BIGINT NOT NULL,
    "battlenet_id" BIGINT NOT NULL,
    "region" SMALLINT NOT NULL,
    "realm" SMALLINT NOT NULL,
    "name" TEXT NOT NULL,
    "clan_id" INTEGER,

    PRIMARY KEY ("id"),

    CONSTRAINT "fk_player_character_account_id"
        FOREIGN KEY ("account_id")
        REFERENCES "account"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "fk_player_character_clan_id"
        FOREIGN KEY ("clan_id")
        REFERENCES "clan"("id")
        ON UPDATE CASCADE,

    CONSTRAINT "uq_player_character_region_realm_battlenet_id"
        UNIQUE ("region", "realm", "battlenet_id")

);

CREATE INDEX "ix_player_character_account_id" ON "player_character"("account_id");
CREATE INDEX "ix_player_character_battlenet_id" ON "player_character"("battlenet_id");
CREATE INDEX "ix_player_character_name" ON "player_character"(LOWER("name") text_pattern_ops);
CREATE INDEX "ix_player_character_clan_id" ON "player_character"("clan_id") WHERE "clan_id" IS NOT NULL;

CREATE TABLE "season"
(

    "id" SERIAL,
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

    "id" SERIAL,
    "season_id" INTEGER NOT NULL,
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

    "id" SERIAL,
    "league_id" INTEGER NOT NULL,
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

    "id" SERIAL,
    "league_tier_id" INTEGER NOT NULL,
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
    "legacy_id" NUMERIC NOT NULL,
    "division_id" INTEGER NOT NULL,
    "season" SMALLINT NOT NULL,
    "region" SMALLINT NOT NULL,
    "league_type" SMALLINT NOT NULL,
    "queue_type" SMALLINT NOT NULL,
    "team_type" SMALLINT NOT NULL,
    "tier_type" SMALLINT,
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

    CONSTRAINT "uq_team_queue_type_region_legacy_id_season"
        UNIQUE ("queue_type", "region", "legacy_id", "season")

);

CREATE INDEX "ix_team_ladder_search_full" ON "team"("season", "queue_type", "team_type", "rating", "id");

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

CREATE TABLE "team_state"
(
    "team_id" BIGINT NOT NULL,
    "timestamp" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    "division_id" INTEGER NOT NULL,
    "games" SMALLINT NOT NULL,
    "rating" SMALLINT NOT NULL,
    "archived" BOOLEAN,
    "secondary" BOOLEAN,

    PRIMARY KEY ("team_id", "timestamp"),

    CONSTRAINT "fk_team_state_team_id"
        FOREIGN KEY ("team_id")
        REFERENCES "team"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "fk_team_state_division_id"
        FOREIGN KEY ("division_id")
        REFERENCES "division"("id")
        ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE INDEX "ix_team_state_timestamp" ON "team_state"("timestamp");
CREATE INDEX "ix_team_state_team_id_archived" ON "team_state"("team_id", "archived") WHERE "archived" = true;
CREATE INDEX "ix_team_state_secondary_timestamp" ON "team_state"("secondary", "timestamp") WHERE "secondary" = true;

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
    "league_id" INTEGER NOT NULL,
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

CREATE TABLE "season_state"
(
    "season_id" INTEGER NOT NULL,
    "period_start" TIMESTAMP WITH TIME ZONE NOT NULL,
    "player_count" SMALLINT NOT NULL,
    "total_games_played" INTEGER NOT NULL,
    "games_played" SMALLINT,

    PRIMARY KEY("period_start", "season_id"),

    CONSTRAINT "fk_season_state_season_id"
        FOREIGN KEY ("season_id")
        REFERENCES "season"("id")
        ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE "player_character_stats"
(
    "id" BIGSERIAL,
    "player_character_id" BIGINT NOT NULL,
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
        ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE UNIQUE INDEX "uq_player_character_stats_main"
    ON "player_character_stats"("player_character_id", COALESCE("race", -32768), "queue_type", "team_type");

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

CREATE TABLE "pro_player"
(
    "id" BIGSERIAL,
    "revealed_id" bytea NOT NULL,
    "aligulac_id" BIGINT,
    "nickname" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "country" CHAR(2),
    "team" TEXT,
    "birthday" DATE,
    "earnings" INTEGER,
    "updated" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    PRIMARY KEY ("id"),

    CONSTRAINT "uq_pro_player_revealed_id"
        UNIQUE("revealed_id")
);

CREATE INDEX "ix_pro_player_updated" ON "pro_player"("updated");
CREATE INDEX "ix_pro_player_nickname" ON "pro_player"(LOWER("nickname"));

CREATE TABLE "social_media_link"
(
    "pro_player_id" BIGINT NOT NULL,
    "type" SMALLINT NOT NULL,
    "url" TEXT NOT NULL,
    "updated" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    PRIMARY KEY("pro_player_id", "type"),

    CONSTRAINT "fk_social_media_link_pro_player_id"
        FOREIGN KEY ("pro_player_id")
        REFERENCES "pro_player"("id")
        ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE INDEX "ix_social_media_link_updated" ON "social_media_link"("updated");

CREATE TABLE "pro_player_account"
(
    "pro_player_id" BIGINT NOT NULL,
    "account_id" BIGINT NOT NULL,
    "updated" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    PRIMARY KEY("account_id"),

    CONSTRAINT "fk_pro_player_account_pro_player_id"
        FOREIGN KEY ("pro_player_id")
        REFERENCES "pro_player"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "fk_pro_player_account_account_id"
        FOREIGN KEY ("account_id")
        REFERENCES "account"("id")
        ON DELETE CASCADE ON UPDATE CASCADE

);

CREATE INDEX "ix_pro_player_account_pro_player_id" ON "pro_player_account"("pro_player_id");
CREATE INDEX "ix_pro_player_account_updated" ON "pro_player_account"("updated");

CREATE TABLE "pro_team"
(
    "id" BIGSERIAL,
    "aligulac_id" BIGINT,
    "name" TEXT NOT NULL,
    "short_name" TEXT,
    "updated" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    PRIMARY KEY("id")
);

CREATE UNIQUE INDEX "uq_pro_team_name" ON "pro_team"(LOWER(REPLACE("name", ' ', '')));
CREATE INDEX "ix_pro_team_updated" ON "pro_team"("updated");

CREATE TABLE "pro_team_member"
(
    "pro_team_id" BIGINT NOT NULL,
    "pro_player_id" BIGINT NOT NULL,
    "updated" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    PRIMARY KEY ("pro_player_id"),

    CONSTRAINT "fk_pro_team_member_pro_team_id"
        FOREIGN KEY ("pro_team_id")
        REFERENCES "pro_team"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "fk_pro_team_member_pro_player_id"
        FOREIGN KEY ("pro_player_id")
        REFERENCES "pro_player"("id")
        ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE INDEX "ix_pro_team_member_pro_team_id" ON "pro_team_member"("pro_team_id");
CREATE INDEX "ix_pro_team_member_updated" ON "pro_team_member"("updated");

CREATE TABLE "match"
(
    "id" BIGSERIAL,
    "date" TIMESTAMP WITH TIME ZONE NOT NULL,
    "type" SMALLINT NOT NULL,
    "map" TEXT NOT NULL,
    "region" SMALLINT NOT NULL,
    "updated" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    PRIMARY KEY ("id"),

    CONSTRAINT "uq_match_date_type_map_region"
        UNIQUE("date", "type", "map", "region")
);

CREATE INDEX "ix_match_updated" ON "match"("updated");

CREATE TABLE "match_participant"
(
    "match_id" BIGINT NOT NULL,
    "player_character_id" BIGINT NOT NULL,
    "team_id" BIGINT,
    "team_state_timestamp" TIMESTAMP WITH TIME ZONE,
    "decision" SMALLINT NOT NULL,

    PRIMARY KEY ("match_id", "player_character_id"),

    CONSTRAINT "fk_match_participant_match_id"
        FOREIGN KEY ("match_id")
        REFERENCES "match"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "fk_match_participant_player_character_id"
        FOREIGN KEY ("player_character_id")
        REFERENCES "player_character"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "fk_match_participant_team_id"
        FOREIGN KEY ("team_id")
        REFERENCES "team"("id")
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT "fk_match_participant_team_state_uid"
        FOREIGN KEY ("team_id", "team_state_timestamp")
        REFERENCES "team_state"("team_id", "timestamp")
        ON DELETE SET NULL ON UPDATE CASCADE
);

CREATE INDEX "ix_match_participant_player_character_id" ON "match_participant"("player_character_id");
CREATE INDEX "ix_match_participant_team_id_team_state_timestamp" ON "match_participant"("team_id", "team_state_timestamp")
    WHERE "team_id" IS NOT NULL
    OR "team_state_timestamp" IS NOT NULL;

CREATE TABLE "var"
(
    "key" TEXT NOT NULL,
    "value" TEXT,

    PRIMARY KEY ("key")
);

CREATE TABLE "player_character_report"
(
    "id" SERIAL,
    "player_character_id" BIGINT NOT NULL,
    "additional_player_character_id" BIGINT,
    "type" SMALLINT NOT NULL,
    "status" BOOLEAN,
    "status_change_timestamp" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    PRIMARY KEY("id"),

    CONSTRAINT "fk_player_character_report_player_character_id"
        FOREIGN KEY ("player_character_id")
        REFERENCES "player_character"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "fk_player_character_report_additional_player_character_id"
        FOREIGN KEY ("additional_player_character_id")
        REFERENCES "player_character"("id")
        ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE UNIQUE INDEX "uq_player_character_report_player_character_id_type_additional_player_character_id"
    ON "player_character_report"("player_character_id", "type", COALESCE("additional_player_character_id", -1));
CREATE INDEX "ix_player_character_report_additional_player_character_id"
    ON "player_character_report"("additional_player_character_id") WHERE "additional_player_character_id" IS NOT NULL;
CREATE INDEX "ix_player_character_report_status_status_change_timestamp"
    ON "player_character_report"("status", "status_change_timestamp");

CREATE TABLE "evidence"
(
    "id" SERIAL,
    "player_character_report_id" INTEGER NOT NULL,
    "reporter_account_id" BIGINT,
    "reporter_ip" bytea NOT NULL,
    "description" VARCHAR(400) NOT NULL,
    "status" BOOLEAN,
    "status_change_timestamp" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    "created" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    PRIMARY KEY("id"),

    CONSTRAINT "fk_evidence_player_character_report_id"
        FOREIGN KEY ("player_character_report_id")
        REFERENCES "player_character_report"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "fk_evidence_reporter_account_id"
        FOREIGN KEY ("reporter_account_id")
        REFERENCES "account"("id")
        ON DELETE SET NULL ON UPDATE CASCADE
);
CREATE INDEX "ix_evidence_created_reporter_ip" ON "evidence"("created", "reporter_ip");
CREATE INDEX "ix_evidence_created_reporter_account_id"
    ON "evidence"("created", "reporter_account_id")
    WHERE "reporter_account_id" IS NOT NULL;
CREATE INDEX "ix_evidence_player_character_report_id" ON "evidence"("player_character_report_id");
CREATE INDEX "ix_evidence_status_status_change_timestamp"
    ON "evidence"("status", "status_change_timestamp");

CREATE TABLE "evidence_vote"
(
    "evidence_id" INTEGER NOT NULL,
    "voter_account_id" BIGINT NOT NULL,
    "evidence_created" TIMESTAMP WITH TIME ZONE NOT NULL,
    "vote" BOOLEAN NOT NULL,
    "updated" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    PRIMARY KEY ("evidence_id", "voter_account_id"),

    CONSTRAINT "fk_evidence_vote_evidence_id"
        FOREIGN KEY ("evidence_id")
        REFERENCES "evidence"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "fk_evidence_vote_voter_account_id"
        FOREIGN KEY ("voter_account_id")
        REFERENCES "account"("id")
        ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE INDEX "ix_evidence_vote_updated" ON "evidence_vote"("updated");

CREATE TYPE player_character_summary AS
(
    player_character_id BIGINT,
    race SMALLINT,
    games SMALLINT,
    rating_avg SMALLINT,
    rating_max SMALLINT
);

CREATE OR REPLACE FUNCTION get_player_character_summary(character_ids BIGINT[], from_timestamp TIMESTAMP WITH TIME ZONE)
RETURNS SETOF player_character_summary
AS
'
DECLARE
    table_record RECORD;
    result player_character_summary[];
    cur_season INTEGER;
    cur_player_character_id BIGINT;
    cur_mmr SMALLINT[];
    cur_games INTEGER DEFAULT -1;
    prev_games INTEGER DEFAULT 0;
    cur_legacy_id NUMERIC;
    cur_legacy_id_text TEXT;
BEGIN
FOR table_record IN
WITH team_filter AS
(
    SELECT
    team.season,
    team.legacy_id,
    team_member.player_character_id,
    team.wins + team.losses + team.ties AS games,
    team.rating,
    season.end::timestamp AS "timestamp"
    FROM team
    INNER JOIN team_member ON team.id = team_member.team_id
    INNER JOIN season ON team.region = season.region AND team.season = season.battlenet_id
    WHERE player_character_id = ANY(character_ids)
    AND team.queue_type = 201
    AND season.end >= from_timestamp
),
team_state_filter AS
(
    SELECT
    team.season,
    team.legacy_id,
    team_member.player_character_id,
    team_state.games,
    team_state.rating,
    team_state.timestamp
    FROM team_state
    INNER JOIN team ON team_state.team_id = team.id
    INNER JOIN team_member ON team.id = team_member.team_id
    WHERE player_character_id = ANY(character_ids)
    AND team.queue_type = 201
    AND team_state.timestamp >= from_timestamp
)
SELECT *
FROM
(
    SELECT * FROM team_filter
    UNION ALL
    SELECT * FROM team_state_filter
) t
ORDER BY player_character_id, legacy_id, season, timestamp
LOOP

    IF cur_legacy_id IS NULL THEN
        cur_legacy_id = table_record.legacy_id;
        cur_player_character_id = table_record.player_character_id;
        cur_season = table_record.season;
    END IF;

    IF table_record.legacy_id <> cur_legacy_id THEN
        cur_legacy_id_text = cur_legacy_id::text;
        result = array_append(result, row
        (
            cur_player_character_id,
            substring(cur_legacy_id_text, char_length(cur_legacy_id_text))::smallint,
            cur_games,
            (SELECT AVG(x) FROM unnest(cur_mmr) x),
            (SELECT MAX(x) FROM unnest(cur_mmr) x)
        )::player_character_summary);
        cur_games = -1;
        cur_mmr = array[]::SMALLINT[];
        cur_legacy_id = table_record.legacy_id;
        cur_player_character_id = table_record.player_character_id;
    END IF;

    cur_games =
    CASE
        WHEN cur_games = -1 THEN 1
        WHEN table_record.season <> cur_season THEN cur_games + table_record.games
        WHEN table_record.games < prev_games THEN cur_games + table_record.games
        ELSE cur_games + (table_record.games - prev_games)
    END;

    IF table_record.season <> cur_season THEN cur_season = table_record.season; END IF;
    prev_games = table_record.games;
    cur_mmr = array_append(cur_mmr, table_record.rating);
END LOOP;

IF cur_games <> -1 THEN
    cur_legacy_id_text = cur_legacy_id::text;
    result = array_append(result, row
    (
        cur_player_character_id,
        substring(cur_legacy_id_text, char_length(cur_legacy_id_text))::smallint,
        cur_games,
        (SELECT AVG(x) FROM unnest(cur_mmr) x),
        (SELECT MAX(x) FROM unnest(cur_mmr) x)
    )::player_character_summary);
END IF;

RETURN QUERY SELECT * FROM unnest(result);
END
'
LANGUAGE plpgsql;
