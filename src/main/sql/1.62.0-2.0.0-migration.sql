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

DROP TABLE IF EXISTS player_character_stats;
