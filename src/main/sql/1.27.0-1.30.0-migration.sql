-- Copyright (C) 2021 Oleksandr Masniuk
-- SPDX-License-Identifier: AGPL-3.0-or-later
---

CREATE TABLE "map"
(
    "id" SERIAL,
    "name" TEXT NOT NULL,

    PRIMARY KEY ("id"),

    CONSTRAINT "uq_map_name"
        UNIQUE("name")
);

INSERT INTO "map"("name")
SELECT DISTINCT("map") FROM "match";

ALTER TABLE "match"
    ADD COLUMN "map_id" INTEGER NOT NULL DEFAULT 0;

UPDATE "match"
SET "map_id" = "map"."id"
FROM "map"
WHERE "map"."name" = "match"."map";

ALTER TABLE "match"
    ALTER COLUMN "map_id" DROP DEFAULT,
    ADD CONSTRAINT "fk_match_map_id"
        FOREIGN KEY ("map_id")
        REFERENCES "map"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    DROP CONSTRAINT "uq_match_date_type_map_region",
    ADD CONSTRAINT "uq_match_date_type_map_id_region"
        UNIQUE("date", "type", "map_id", "region"),
    DROP COLUMN "map";

ALTER TABLE "match"
    ADD COLUMN "duration" SMALLINT;

WITH 
match_filter AS
(
    SELECT player_character_id, date
    FROM match
    INNER JOIN match_participant ON match.id = match_participant.match_id
    WHERE match.date >= NOW() - INTERVAL '450 minutes'
),
match_duration AS
(
    SELECT id, EXTRACT(EPOCH FROM (match.date - MAX(match_duration.date))) AS duration
    FROM match
    INNER JOIN match_participant ON match.id = match_participant.match_id
    JOIN LATERAL
    (
        SELECT match_filter.date
        FROM match_filter
        WHERE match_filter.player_character_id = match_participant.player_character_id
        AND match_filter.date < match.date
        ORDER BY match_filter.date DESC
        LIMIT 1
    ) match_duration ON true
    WHERE match.date >= NOW() - INTERVAL '360 minutes'
    GROUP BY match.id
)
UPDATE match
SET duration = match_duration.duration
FROM match_duration
WHERE match.id = match_duration.id
AND match_duration.duration BETWEEN 1 AND 5400
AND (match.duration IS NULL OR match.duration > match_duration.duration);

CREATE TABLE "map_stats"
(
    "id" SERIAL,
    "map_id" INTEGER,
    "league_id" INTEGER NOT NULL,
    "race" SMALLINT NOT NULL,
    "versus_race" SMALLINT NOT NULL,
    "games" SMALLINT NOT NULL,
    "games_with_duration" SMALLINT NOT NULL,
    "wins" SMALLINT NOT NULL,
    "losses" SMALLINT NOT NULL,
    "ties" SMALLINT NOT NULL,
    "duration" INTEGER NOT NULL,

    PRIMARY KEY("id"),

    CONSTRAINT "fk_map_stats_map_id"
        FOREIGN KEY ("map_id")
        REFERENCES "map"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "fk_map_stats_league_id"
        FOREIGN KEY ("league_id")
        REFERENCES "league"("id")
        ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE UNIQUE INDEX "uq_map_stats_league_id_map_id_race_versus_race"
    ON "map_stats"("league_id", COALESCE("map_id", -1), "race", "versus_race");

CREATE OR REPLACE FUNCTION get_top_percentage_league_lotv(rank INTEGER, teamCount DOUBLE PRECISION, gm BOOLEAN)
RETURNS SMALLINT
AS
'
DECLARE percentage DOUBLE PRECISION;
BEGIN
percentage = (rank / teamCount) * 100;
RETURN
CASE
    WHEN gm = true AND rank <= 200 THEN 6
    WHEN percentage <= 4 THEN 5
    WHEN percentage <= 27 THEN 4
    WHEN percentage <= 50 THEN 3
    WHEN percentage <= 73 THEN 2
    WHEN percentage <= 96 THEN 1
    ELSE 0
END;
END
'
LANGUAGE plpgsql;

DROP TYPE player_character_summary CASCADE;
CREATE TYPE player_character_summary AS
(
    player_character_id BIGINT,
    race SMALLINT,
    games SMALLINT,
    rating_avg SMALLINT,
    rating_max SMALLINT,
    rating_cur SMALLINT
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
        (SELECT MAX(x) FROM unnest(cur_mmr) x),
        cur_mmr[array_upper(cur_mmr, 1)]
    )::player_character_summary);
END IF;

RETURN QUERY SELECT * FROM unnest(result);
END
'
LANGUAGE plpgsql;
