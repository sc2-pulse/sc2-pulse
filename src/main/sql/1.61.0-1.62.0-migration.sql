ALTER TABLE "league_tier"
    ALTER COLUMN "type" DROP NOT NULL,
    DROP CONSTRAINT "uq_league_tier_league_id_type";
CREATE UNIQUE INDEX "uq_league_tier_league_id_type" ON "league_tier"("league_id", COALESCE("type", -1));


WITH season_filter AS
(
    SELECT DISTINCT ON(region, season)
    region, season AS battlenet_id
    FROM team
    WHERE team.tier_type IS NULL
),
league_filter AS
(
    SELECT league.id
    FROM season_filter
    INNER JOIN season USING(region, battlenet_id)
    INNER JOIN league ON season.id = league.season_id
)
INSERT INTO league_tier(league_id)
SELECT id
FROM league_filter;

WITH league_tier_filter AS
(
    SELECT id, league_id
    FROM league_tier
    WHERE type IS NULL
)
UPDATE division
SET league_tier_id = league_tier_filter.id
FROM team
INNER JOIN division team_division ON team.division_id = team_division.id
INNER JOIN league_tier ON team_division.league_tier_id = league_tier.id
INNER JOIN league_tier_filter USING(league_id)
WHERE team.tier_type IS NULL
AND league_tier.type != 0
AND division.id = team_division.id;

ALTER TABLE "season"
    ALTER COLUMN "start" TYPE TIMESTAMP WITH TIME ZONE,
    ALTER COLUMN "end" TYPE TIMESTAMP WITH TIME ZONE;

WITH nonapa_season AS
(
    VALUES
    (5, 28, to_timestamp(1468303201), to_timestamp(1476770400)),
    (5, 29, to_timestamp(1476770401), to_timestamp(1479794400)),
    (5, 30, to_timestamp(1479794401), to_timestamp(1485237600)),
    (5, 31, to_timestamp(1485237601), to_timestamp(1493704800)),
    (5, 32, to_timestamp(1493704801), to_timestamp(1500444000)),
    (5, 33, to_timestamp(1500444001), to_timestamp(1508392800)),
    (5, 34, to_timestamp(1508392801), to_timestamp(1516687200)),
    (5, 35, to_timestamp(1516687201), to_timestamp(1526364000)),
    (5, 36, to_timestamp(1526364001), to_timestamp(1534226400)),
    (5, 37, to_timestamp(1534226401), to_timestamp(1542693600)),
    (5, 38, to_timestamp(1542693601), to_timestamp(1548136800)),
    (5, 39, to_timestamp(1548136801), to_timestamp(1558504800)),
    (5, 40, to_timestamp(1558504801), to_timestamp(1566453600)),
    (5, 41, to_timestamp(1566453601), to_timestamp(1574661600)),
    (5, 42, to_timestamp(1574661601), to_timestamp(1584424800)),
    (5, 43, to_timestamp(1584424801), to_timestamp(1591768800)),
    (5, 44, to_timestamp(1591728971), to_timestamp(1601532000)),
    (5, 45, to_timestamp(1601498865), to_timestamp(1611640800)),
    (5, 46, to_timestamp(1611615600), to_timestamp(1617206400)),
    (5, 47, to_timestamp(1617667200), to_timestamp(1626760800)),
    (5, 48, to_timestamp(1626760800), to_timestamp(1636473600)),
    (5, 49, to_timestamp(1636473600), to_timestamp(1641312000)),
    (5, 50, to_timestamp(1641312000), to_timestamp(1648742400)),
    (5, 51, to_timestamp(1648742400), to_timestamp(1658296800)),
    (5, 52, to_timestamp(1658296800), to_timestamp(1668009600)),
    (5, 53, to_timestamp(1668009600), to_timestamp(1672848000)),
    (5, 54, to_timestamp(1672848000), to_timestamp(1680278400))
)
    UPDATE season
    SET "start" = s."start",
    "end" = s."end"
    FROM nonapa_season s(region, battlenet_id, "start", "end")
    WHERE season.region = s.region
    AND season.battlenet_id = s.battlenet_id;

WITH next_season_start AS
(
    SELECT season.id,
    season_next."start"
    FROM season
    INNER JOIN season season_next ON season.region = season_next.region
        AND season.battlenet_id + 1 = season_next.battlenet_id
)
    UPDATE season
    SET "end" = next_season_start."start"
    FROM next_season_start
    WHERE season.id = next_season_start.id
    AND season."end" != next_season_start."start";

CREATE TABLE team_state_archive
(
    "team_id" BIGINT NOT NULL,
    "timestamp" TIMESTAMP WITH TIME ZONE NOT NULL,

    PRIMARY KEY ("team_id", "timestamp"),

    CONSTRAINT "fk_team_state_archive_team_id_timestamp"
        FOREIGN KEY ("team_id", "timestamp")
        REFERENCES "team_state"("team_id", "timestamp")
        ON DELETE CASCADE ON UPDATE CASCADE
);

INSERT INTO team_state_archive(team_id, timestamp)
SELECT team_id, timestamp
FROM team_state
WHERE archived = true;

WITH max_season AS
(
    SELECT region, MAX(battlenet_id) AS season
    FROM season
    GROUP BY region
),
current_season_team AS
(
    SELECT id
    FROM max_season
    INNER JOIN team USING(region, season)
)
DELETE FROM team_state_archive
USING current_season_team
WHERE team_state_archive.team_id = current_season_team.id;

ALTER TABLE team_state
DROP COLUMN archived;

VACUUM(ANALYZE) team_state, team_state_archive;
REINDEX "team_state_archive_pkey";

DO
$do$
DECLARE
    seasonId INTEGER;
    seasonMin SMALLINT;
    teamId BIGINT;
    teamRegion SMALLINT;
    removedCurrent INTEGER;
    removedTotal INTEGER;
    minLastPlayedAll JSONB;
    minLastPlayed TIMESTAMP WITH TIME ZONE;
BEGIN

SELECT season INTO seasonMin
FROM team_state
INNER JOIN team ON team_state.team_id = team.id
WHERE timestamp = (SELECT MIN(timestamp) FROM team_state)
LIMIT 1;
RAISE NOTICE 'Starting from season %', seasonMin;
FOR seasonId IN SELECT DISTINCT(battlenet_id) FROM season WHERE battlenet_id >= seasonMin ORDER BY battlenet_id LOOP

removedTotal := 0;
WITH vals AS
(
    SELECT team.region,
    COALESCE(MAX(timestamp), MAX(last_played), MAX(season."end")) + INTERVAL '2 second' AS last_played
    FROM team
    LEFT JOIN team_state ON team.id = team_state.team_id
    INNER JOIN season ON team.region = season.region AND team.season = season.battlenet_id
    WHERE season = seasonId - 1
    GROUP BY team.region
)
SELECT jsonb_object_agg(region, last_played) INTO minLastPlayedAll FROM vals;

FOR teamId, teamRegion IN SELECT id, region FROM team WHERE season = seasonId LOOP
SELECT (minLastPlayedAll->>teamRegion::text)::TIMESTAMP WITH TIME ZONE INTO minLastPlayed;

DELETE FROM team_state
WHERE team_state.team_id = teamId
AND team_state.timestamp < minLastPlayed;
GET DIAGNOSTICS removedCurrent = ROW_COUNT;
removedTotal := removedTotal + removedCurrent;
END LOOP;

RAISE NOTICE 'Updated season %, removed %', seasonId, removedTotal;
END LOOP;

END
$do$
LANGUAGE plpgsql;

DO
$do$
DECLARE
    seasonId INTEGER;
    createdCurrent INTEGER;
BEGIN
FOR seasonId IN SELECT DISTINCT(battlenet_id) FROM season ORDER BY battlenet_id LOOP

createdCurrent := 0;
WITH snapshot_timestamp AS
(
    SELECT team.region,
    COALESCE(MAX(timestamp), MAX(last_played), MAX(season."end")) + INTERVAL '1 second' AS timestamp
    FROM team
    LEFT JOIN team_state ON team.id = team_state.team_id
    INNER JOIN season ON team.region = season.region AND team.season = season.battlenet_id
    WHERE season = seasonId
    GROUP BY team.region
),
current_season AS
(
    SELECT region, MAX(battlenet_id) AS battlenet_id
    FROM season
    GROUP BY region
),
last_snapshots AS
(
    INSERT INTO team_state
    (
        "team_id",
        "timestamp",
        "division_id",
        "population_state_id",
        "wins",
        "games",
        "rating",
        "global_rank",
        "region_rank",
        "league_rank",
        "secondary"
    )
        SELECT
        "id",
        "timestamp",
        "division_id",
        "population_state_id",
        "wins",
        "wins" + "losses" + "ties",
        "rating",
        "global_rank",
        "region_rank",
        "league_rank",
        CASE WHEN queue_type != 201 THEN true ELSE NULL END
        FROM team
        INNER JOIN snapshot_timestamp USING(region)
        INNER JOIN current_season USING(region)
        WHERE season = seasonId
        AND season != current_season.battlenet_id
        RETURNING team_id, timestamp
)
    INSERT INTO team_state_archive(team_id, timestamp)
    SELECT team_id, timestamp
    FROM last_snapshots;

GET DIAGNOSTICS createdCurrent = ROW_COUNT;
RAISE NOTICE 'Updated season %, created %', seasonId, createdCurrent;
END LOOP;

END
$do$
LANGUAGE plpgsql;

ALTER TABLE team_state
ADD COLUMN "region_team_count" INTEGER;

DO
$do$
DECLARE
    seasonId INTEGER;
    seasonMin SMALLINT;
    teamId BIGINT;
BEGIN
SELECT season INTO seasonMin
FROM team_state
INNER JOIN team ON team_state.team_id = team.id
WHERE timestamp = (SELECT MIN(timestamp) FROM team_state)
LIMIT 1;
RAISE NOTICE 'Starting from season %', seasonMin;
FOR seasonId IN SELECT DISTINCT(battlenet_id) FROM season WHERE battlenet_id >= seasonMin ORDER BY battlenet_id LOOP
FOR teamId IN SELECT id FROM team WHERE season = seasonId LOOP

UPDATE team_state
SET region_team_count = population_state.region_team_count
FROM population_state
WHERE team_state.team_id = teamId
AND team_state.population_state_id = population_state.id;
END LOOP;
RAISE NOTICE 'Updated season % region_team_count, ', seasonId;
END LOOP;

END
$do$
LANGUAGE plpgsql;


CREATE UNIQUE INDEX CONCURRENTLY team_state_pkey2 ON team_state(team_id, timestamp)
    INCLUDE(rating, games, division_id, region_rank, region_team_count);
ALTER TABLE team_state
    DROP CONSTRAINT team_state_pkey CASCADE,
    ADD CONSTRAINT team_state_pkey PRIMARY KEY USING INDEX team_state_pkey2;
ALTER TABLE match_participant
    ADD CONSTRAINT "fk_match_participant_team_state_uid"
    FOREIGN KEY ("team_id", "team_state_timestamp")
    REFERENCES "team_state"("team_id", "timestamp")
    ON DELETE SET NULL ON UPDATE CASCADE;
ALTER TABLE team_state_archive
    ADD CONSTRAINT "fk_team_state_archive_team_id_timestamp"
    FOREIGN KEY ("team_id", "timestamp")
    REFERENCES "team_state"("team_id", "timestamp")
    ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE team DROP CONSTRAINT uq_team_queue_type_region_legacy_id_season;
ALTER TABLE team ALTER COLUMN legacy_id TYPE TEXT COLLATE "C";

DO
$do$
DECLARE
    seasonId INTEGER;
    teamId BIGINT;
BEGIN
FOR seasonId IN SELECT DISTINCT(battlenet_id) FROM season ORDER BY battlenet_id LOOP
FOR teamId IN SELECT id FROM team WHERE season = seasonId LOOP
    WITH new_legacy_id AS
    (
        SELECT team_id AS id,
        string_agg
        (
            player_character.realm::text
            || '.' || player_character.battlenet_id::text
            || '.' || CASE
                WHEN team_queue_type != 201 THEN ''
                WHEN team_member.terran_games_played > COALESCE(team_member.protoss_games_played, 0)
                    AND team_member.terran_games_played > COALESCE(team_member.zerg_games_played, 0)
                    AND team_member.terran_games_played > COALESCE(team_member.random_games_played, 0)
                    THEN '1'
                WHEN team_member.protoss_games_played > COALESCE(team_member.terran_games_played, 0)
                    AND team_member.protoss_games_played > COALESCE(team_member.zerg_games_played, 0)
                    AND team_member.protoss_games_played > COALESCE(team_member.random_games_played, 0)
                    THEN '2'
                WHEN team_member.zerg_games_played > COALESCE(team_member.terran_games_played, 0)
                    AND team_member.zerg_games_played > COALESCE(team_member.protoss_games_played, 0)
                    AND team_member.zerg_games_played > COALESCE(team_member.random_games_played, 0)
                    THEN '3'
                WHEN team_member.random_games_played > COALESCE(team_member.terran_games_played, 0)
                    AND team_member.random_games_played > COALESCE(team_member.protoss_games_played, 0)
                    AND team_member.random_games_played > COALESCE(team_member.zerg_games_played, 0)
                    THEN '4'
                WHEN team_member.terran_games_played > 0 THEN '1'
                WHEN team_member.protoss_games_played > 0 THEN '2'
                WHEN team_member.zerg_games_played > 0 THEN '3'
                WHEN team_member.random_games_played > 0 THEN '4'
            END,
            '~' ORDER BY player_character.realm, player_character.battlenet_id
        ) AS legacy_id
        FROM team_member
        INNER JOIN player_character ON team_member.player_character_id = player_character.id
        WHERE team_id = teamId
        GROUP BY team_id
    )
        UPDATE team
        SET legacy_id = new_legacy_id.legacy_id
        FROM new_legacy_id
        WHERE team.id = new_legacy_id.id;
        END LOOP;
        RAISE NOTICE 'Updated team.legacy_id for season %', seasonId;
END LOOP;
END
$do$
LANGUAGE plpgsql;

ALTER TABLE team
    ADD CONSTRAINT "uq_team_queue_type_region_legacy_id_season"
        UNIQUE ("queue_type", "region", "legacy_id", "season");

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
    INNER JOIN season ON team.region = season.region AND team.season = season.battlenet_id
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

ALTER TABLE team
    DROP CONSTRAINT "uq_team_queue_type_region_legacy_id_season",
    ADD CONSTRAINT "uq_team_legacy_natural_id"
        UNIQUE ("queue_type", "team_type", "region", "legacy_id", "season");
