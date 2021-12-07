-- Copyright (C) 2021 Oleksandr Masniuk
-- SPDX-License-Identifier: AGPL-3.0-or-later
---

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

DROP FUNCTION get_player_character_summary(character_ids BIGINT[], from_timestamp TIMESTAMP WITH TIME ZONE);