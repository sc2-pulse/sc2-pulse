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
