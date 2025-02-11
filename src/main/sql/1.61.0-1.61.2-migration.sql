ALTER TABLE team ADD COLUMN "joined" TIMESTAMP WITH TIME ZONE;

CREATE TABLE bug_team_state_duplicate_team_2025_02_03
(
    team_id BIGINT NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    division_id INTEGER NOT NULL,
    population_state_id INTEGER,
    wins SMALLINT,
    games SMALLINT NOT NULL,
    rating SMALLINT NOT NULL,
    global_rank INTEGER,
    region_rank INTEGER,
    league_rank INTEGER,
    archived BOOLEAN,
    secondary BOOLEAN
);

DO
$do$
DECLARE
    seasonId INTEGER;
    seasonMin INTEGER;
    teamId BIGINT;
    removedCurrent INTEGER;
    removedSeason INTEGER;
BEGIN

SELECT season INTO seasonMin
FROM team_state
INNER JOIN team ON team_state.team_id = team.id
WHERE timestamp = (SELECT MIN(timestamp) FROM team_state)
LIMIT 1;
RAISE NOTICE 'Starting from season %', seasonMin;
FOR seasonId IN SELECT DISTINCT(battlenet_id) FROM season WHERE battlenet_id >= seasonMin ORDER BY battlenet_id LOOP
removedSeason := 0;
FOR teamId IN SELECT id FROM team WHERE season = seasonId LOOP
removedCurrent := 0;

WITH
to_delete AS
(
    SELECT
    team_id,
    timestamp,
    division_id,
    population_state_id,
    wins,
    games,
    rating,
    global_rank,
    region_rank,
    league_rank,
    archived,
    secondary
    FROM
    (
        SELECT
        team_id,
        timestamp,
        division_id,
        population_state_id,
        wins,
        games,
        rating,
        global_rank,
        region_rank,
        league_rank,
        archived,
        secondary,

        LEAD(games) OVER(partition by team_id ORDER BY timestamp) AS next_games,

        LAG(division_id) OVER(partition by team_id ORDER BY timestamp) AS prev_division_id,
        LEAD(division_id) OVER(partition by team_id ORDER BY timestamp) AS next_division_id


        FROM team_state
        WHERE team_state.team_id = teamId
    ) q
        WHERE
        (
            division_id != prev_division_id
            AND division_id != next_division_id
        )
        OR
        (
            division_id != prev_division_id
            AND division_id = next_division_id
            AND games > next_games
        )

)
    INSERT INTO bug_team_state_duplicate_team_2025_02_03
    (
        team_id,
        timestamp,
        division_id,
        population_state_id,
        wins,
        games,
        rating,
        global_rank,
        region_rank,
        league_rank,
        archived,
        secondary
    )
    SELECT
    team_id,
    timestamp,
    division_id,
    population_state_id,
    wins,
    games,
    rating,
    global_rank,
    region_rank,
    league_rank,
    archived,
    secondary
    FROM to_delete;

GET DIAGNOSTICS removedCurrent = ROW_COUNT;
removedSeason = removedSeason + removedCurrent;

END LOOP;
RAISE NOTICE 'Updated season %, removed %', seasonId, removedSeason;
END LOOP;

END
$do$
LANGUAGE plpgsql;


DO
$do$
DECLARE
    seasonId INTEGER;
    seasonMin INTEGER;
    teamId BIGINT;
    removedCurrent INTEGER;
    removedSeason INTEGER;
BEGIN

SELECT season INTO seasonMin
FROM team_state
INNER JOIN team ON team_state.team_id = team.id
WHERE timestamp = (SELECT MIN(timestamp) FROM team_state)
LIMIT 1;
RAISE NOTICE 'Starting from season %', seasonMin;
FOR seasonId IN SELECT DISTINCT(battlenet_id) FROM season WHERE battlenet_id >= seasonMin ORDER BY battlenet_id LOOP
removedSeason := 0;
FOR teamId IN SELECT id FROM team WHERE season = seasonId LOOP
removedCurrent := 0;

WITH
to_delete AS
(
    SELECT
    team_id,
    timestamp,
    division_id,
    population_state_id,
    wins,
    games,
    rating,
    global_rank,
    region_rank,
    league_rank,
    archived,
    secondary
    FROM
    (
        SELECT
        team_id,
        timestamp,
        division_id,
        population_state_id,
        wins,
        games,
        rating,
        global_rank,
        region_rank,
        league_rank,
        archived,
        secondary,

        LAG(games) OVER(partition by team_id ORDER BY timestamp) AS prev_games,
        LAG(division_id) OVER(partition by team_id ORDER BY timestamp) AS prev_division_id,
        LAG(rating) OVER(partition by team_id ORDER BY timestamp) AS prev_rating,
        LAG(wins) OVER(partition by team_id ORDER BY timestamp) AS prev_wins
        FROM team_state
        WHERE team_state.team_id = teamId
    ) q
        WHERE
        (
            division_id = prev_division_id
            AND games = prev_games
            AND rating = prev_rating
            AND wins IS NOT DISTINCT FROM prev_wins
        )
)
    INSERT INTO bug_team_state_duplicate_team_2025_02_03
    (
        team_id,
        timestamp,
        division_id,
        population_state_id,
        wins,
        games,
        rating,
        global_rank,
        region_rank,
        league_rank,
        archived,
        secondary
    )
    SELECT
    team_id,
    timestamp,
    division_id,
    population_state_id,
    wins,
    games,
    rating,
    global_rank,
    region_rank,
    league_rank,
    archived,
    secondary
    FROM to_delete;

GET DIAGNOSTICS removedCurrent = ROW_COUNT;
removedSeason = removedSeason + removedCurrent;

END LOOP;
RAISE NOTICE 'Updated season %, detected %', seasonId, removedSeason;
END LOOP;

END
$do$
LANGUAGE plpgsql;

DELETE FROM team_state
USING bug_team_state_duplicate_team_2025_02_03
WHERE team_state.team_id = bug_team_state_duplicate_team_2025_02_03.team_id
AND team_state.timestamp = bug_team_state_duplicate_team_2025_02_03.timestamp;

DROP TABLE bug_team_state_duplicate_team_2025_02_03;
