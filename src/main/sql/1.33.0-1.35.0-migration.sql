-- Copyright (C) 2021 Oleksandr Masniuk
-- SPDX-License-Identifier: AGPL-3.0-or-later
---

DROP TYPE player_character_summary CASCADE;

CREATE TYPE player_character_summary AS
(
    player_character_id BIGINT,
    race SMALLINT,
    games INTEGER,
    rating_avg SMALLINT,
    rating_max SMALLINT,
    rating_last SMALLINT,
    league_type_last SMALLINT,
    global_rank_last INTEGER
);

CREATE OR REPLACE FUNCTION get_player_character_summary
(character_ids BIGINT[], from_timestamp TIMESTAMP WITH TIME ZONE, races SMALLINT[])
RETURNS SETOF player_character_summary
AS
'
DECLARE
    table_record RECORD;
    prev_table_record RECORD;
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
    team.league_type,
    team.global_rank,
    team.legacy_id,
    team_member.player_character_id,
    team.wins + team.losses + team.ties AS games,
    team.rating,
    (season.end + INTERVAL ''7 days'')::timestamp AS "timestamp"
    FROM team
    INNER JOIN team_member ON team.id = team_member.team_id
    INNER JOIN season ON team.region = season.region AND team.season = season.battlenet_id
    WHERE player_character_id = ANY(character_ids)
    AND team.queue_type = 201
    AND substring(team.legacy_id::text, char_length(team.legacy_id::text))::smallint = ANY(races)
    AND season.end >= from_timestamp
),
team_state_filter AS
(
    SELECT
    team.season,
    null::INTEGER AS league_type,
    null::INTEGER AS global_rank,
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
    AND substring(team.legacy_id::text, char_length(team.legacy_id::text))::smallint = ANY(races)
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
            (SELECT MAX(x) FROM unnest(cur_mmr) x),
            cur_mmr[array_upper(cur_mmr, 1)],
            prev_table_record.league_type,
            prev_table_record.global_rank
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
    prev_table_record = table_record;
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
        cur_mmr[array_upper(cur_mmr, 1)],
        table_record.league_type,
        table_record.global_rank
    )::player_character_summary);
END IF;

RETURN QUERY SELECT * FROM unnest(result);
END
'
LANGUAGE plpgsql;

ALTER TABLE "clan"
    ADD COLUMN "members" SMALLINT,
    ADD COLUMN "active_members" SMALLINT,
    ADD COLUMN "avg_rating" SMALLINT,
    ADD COLUMN "avg_league_type" SMALLINT,
    ADD COLUMN "games" INTEGER;

CREATE INDEX "ix_clan_search_members"
    ON "clan"("members", "id", "active_members", "avg_rating", ("games"::double precision / "active_members" / 60), "region")
    WHERE "active_members" IS NOT NULL;
CREATE INDEX "ix_clan_search_active_members"
    ON "clan"("active_members", "id", ("games"::double precision / "active_members" / 60), "avg_rating", "region")
    WHERE "active_members" IS NOT NULL;
CREATE INDEX "ix_clan_search_avg_rating"
    ON "clan"("avg_rating", "id", ("games"::double precision / "active_members" / 60), "active_members", "region")
    WHERE "active_members" IS NOT NULL;
CREATE INDEX "ix_clan_search_games"
    ON "clan"(("games"::double precision / "active_members" / 60), "id", "active_members", "avg_rating", "region")
    WHERE "active_members" IS NOT NULL;
CREATE INDEX "ix_clan_name" ON "clan"(LOWER("name") text_pattern_ops) WHERE "name" IS NOT NULL;

CREATE OR REPLACE FUNCTION get_favorite_race
(terranGames SMALLINT, protossGames SMALLINT, zergGames SMALLINT, randomGames SMALLINT)
RETURNS SMALLINT
AS
'
    DECLARE
        maxGames SMALLINT;
    BEGIN
        SELECT MAX(x)::smallint INTO maxGames FROM unnest(ARRAY[terranGames, protossGames, zergGames, randomGames]) x;
        return CASE
            WHEN terranGames = maxGames THEN 1
            WHEN protossGames = maxGames THEN 2
            WHEN zergGames = maxGames THEN 3
            ELSE 4
        END;
    END
'
LANGUAGE plpgsql;