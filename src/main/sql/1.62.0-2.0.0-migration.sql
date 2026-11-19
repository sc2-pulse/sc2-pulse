CREATE OR REPLACE VIEW team_state_denormalized AS
SELECT
concat_ws('-', team.queue_type::text, team.team_type::text, team.region::text,  team.legacy_id) AS team_legacy_uid,
team_state.timestamp,
division.battlenet_id AS division_battlenet_id,
league.type AS league_type,
league_tier.type AS tier_type,
team.season,
team_state.wins,
team_state.games,
CASE
    WHEN prev_team_state.games IS NULL THEN team_state.games
    WHEN team_state.games - prev_team_state.games < 0
        OR team_state.team_id != prev_team_state.team_id
        OR
        (
            team_state.games = prev_team_state.games
            AND team_state.rating != prev_team_state.rating
        )
    THEN team_state.games
    ELSE team_state.games - prev_team_state.games
END AS games_delta,
team_state.rating,
team_state.global_rank,
team_state.region_rank,
team_state.league_rank,
population_state.global_team_count,
team_state.region_team_count,
population_state.league_team_count,
team_state.games IS DISTINCT FROM prev_team_state.games
OR team_state.rating IS DISTINCT FROM prev_team_state.rating
OR team_state.team_id IS DISTINCT FROM prev_team_state.team_id AS source
FROM team_state
LEFT JOIN team ON team_state.team_id = team.id
LEFT JOIN division ON team_state.division_id = division.id
LEFT JOIN league_tier ON division.league_tier_id = league_tier.id
LEFT JOIN league ON league_tier.league_id = league.id
LEFT JOIN population_state ON team_state.population_state_id = population_state.id
LEFT JOIN LATERAL
(
    SELECT ts2.games, ts2.team_id, ts2.rating
    FROM team_state ts2
    LEFT JOIN team t2 ON ts2.team_id = t2.id
    WHERE team.queue_type = t2.queue_type
    AND team.team_type = t2.team_type
    AND team.region = t2.region
    AND team.legacy_id = t2.legacy_id
    AND ts2.timestamp < team_state.timestamp
    ORDER BY ts2.timestamp DESC
    LIMIT 1
) prev_team_state ON true
ORDER BY team_state.timestamp;

ALTER TABLE evidence ADD COLUMN archived BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE player_character_report ADD COLUMN archived BOOLEAN NOT NULL DEFAULT false;

DROP TABLE player_character_stats;

DROP TABLE team_state_archive;
DELETE FROM var WHERE key LIKE '%.mmr.history.archive.season'
    OR key = 'mmr.history.clear.update.context.timestamp';

CREATE TABLE "donation"
(
    "id" SERIAL,
    "platform" TEXT NOT NULL,
    "donation_id" TEXT NOT NULL,
    "donor_id" TEXT,
    "donor_name" TEXT,
    "money_original_amount" NUMERIC NOT NULL,
    "money_original_currency" TEXT NOT NULL,
    "money_target_amount" NUMERIC,
    "money_target_currency" TEXT NOT NULL,
    "created" TIMESTAMP WITH TIME ZONE NOT NULL,

    PRIMARY KEY("id"),

    CONSTRAINT "uq_donation_platform_donation_id"
        UNIQUE("platform", "donation_id")
);
CREATE INDEX "ix_donation_created_id" ON "donation"("created", "id");

CREATE TABLE "patreon_donation_state"
(
    "member_id" TEXT,
    "lifetime_amount" NUMERIC NOT NULL,
    "currency" TEXT NOT NULL,
    "version" SMALLINT NOT NULL,

    PRIMARY KEY("member_id")
);

DO
'
BEGIN
    IF EXISTS
    (
        SELECT battlenet_id
        FROM season
        GROUP BY battlenet_id
        HAVING COUNT(DISTINCT (year, number)) > 1
    )
    OR EXISTS
    (
        SELECT year, number
        FROM season
        GROUP BY year, number
        HAVING COUNT(DISTINCT battlenet_id) > 1
    )
    THEN
        RAISE EXCEPTION ''Inconsistent season battlenet_id/year/number mapping'';
    END IF;
END
';

CREATE TABLE "pulse_season"
(
    "id" SMALLINT NOT NULL,
    "year" SMALLINT NOT NULL,
    "number" SMALLINT NOT NULL,

    PRIMARY KEY ("id"),

    CONSTRAINT "uq_pulse_season_year_number"
        UNIQUE ("year", "number"),
    CONSTRAINT "uq_pulse_season_id_year_number"
        UNIQUE ("id", "year", "number")
);

INSERT INTO pulse_season(id, year, number)
SELECT DISTINCT battlenet_id, year, number
FROM season;

ALTER TABLE "season"
    ADD COLUMN "pulse_season_id" SMALLINT;
UPDATE season SET pulse_season_id = battlenet_id;
ALTER TABLE "season"
    ALTER COLUMN "pulse_season_id" SET NOT NULL,
    ADD CONSTRAINT "fk_season_pulse_season_id_year_number"
        FOREIGN KEY ("pulse_season_id", "year", "number")
        REFERENCES "pulse_season"("id", "year", "number")
        ON DELETE RESTRICT ON UPDATE CASCADE,
    ADD CONSTRAINT "uq_season_region_pulse_season_id"
        UNIQUE ("region", "pulse_season_id");

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
    cur_legacy_id TEXT;
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
    INNER JOIN season ON team.region = season.region AND team.season = season.pulse_season_id
    WHERE player_character_id = ANY(character_ids)
    AND team.queue_type = 201
    AND substring(team.legacy_id, char_length(team.legacy_id))::smallint = ANY(races)
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
    AND substring(team.legacy_id, char_length(team.legacy_id))::smallint = ANY(races)
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
        result = array_append(result, row
        (
            cur_player_character_id,
            substring(cur_legacy_id, char_length(cur_legacy_id))::smallint,
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
    result = array_append(result, row
    (
        cur_player_character_id,
        substring(cur_legacy_id, char_length(cur_legacy_id))::smallint,
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

