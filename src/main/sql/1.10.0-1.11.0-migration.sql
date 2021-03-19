-- Copyright (C) 2021 Oleksandr Masniuk and contributors
-- SPDX-License-Identifier: AGPL-3.0-or-later
---

VACUUM(ANALYZE);
SET work_mem = '128MB';

DROP INDEX "ix_player_character_stats_calculation";
DELETE FROM "player_character_stats" WHERE "season_id" IS NOT NULL;
ALTER TABLE "player_character_stats" DROP COLUMN "season_id";
CREATE UNIQUE INDEX "uq_player_character_stats_main"
    ON "player_character_stats"("player_character_id", COALESCE("race", -32768), "queue_type", "team_type");
VACUUM(FULL) "player_character_stats";

ALTER SEQUENCE "season_id_seq" AS INTEGER;
ALTER TABLE "season" ALTER COLUMN "id" TYPE INTEGER;
ALTER TABLE "league" ALTER COLUMN "season_id" TYPE INTEGER;

ALTER SEQUENCE "league_id_seq" AS INTEGER;
ALTER TABLE "league" ALTER COLUMN "id" TYPE INTEGER;
ALTER TABLE "league_tier" ALTER COLUMN "league_id" TYPE INTEGER;
ALTER TABLE "league_stats" ALTER COLUMN "league_id" TYPE INTEGER;

ALTER SEQUENCE "league_tier_id_seq" AS INTEGER;
ALTER TABLE "league_tier" ALTER COLUMN "id" TYPE INTEGER;
ALTER TABLE "division" ALTER COLUMN "league_tier_id" TYPE INTEGER;

ALTER SEQUENCE "division_id_seq" AS INTEGER;
ALTER TABLE "division" ALTER COLUMN "id" TYPE INTEGER;
ALTER TABLE "team" ALTER COLUMN "division_id" TYPE INTEGER;
ALTER TABLE "team_state" ALTER COLUMN "division_id" TYPE INTEGER;

ALTER TABLE "team" ADD COLUMN "league_tier_id" INTEGER NOT NULL DEFAULT 0;

VACUUM(ANALYZE);

ALTER TABLE "team" DROP CONSTRAINT "uq_team_region_battlenet_id";
DROP INDEX "ix_team_ladder_search_full";
ALTER TABLE "team"
    DROP COLUMN "season",
    DROP COLUMN "league_type",
    DROP COLUMN "queue_type",
    DROP COLUMN "team_type",
    DROP COLUMN "tier_type";
UPDATE "team"
    SET "league_tier_id" = "division"."league_tier_id"
    FROM "division"
    WHERE "team"."division_id" = "division"."id";
ALTER TABLE "team"
    ALTER COLUMN "league_tier_id" DROP DEFAULT,
    ADD CONSTRAINT "fk_team_league_tier_id"
        FOREIGN KEY ("league_tier_id")
        REFERENCES "league_tier"("id")
        ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "team" ADD CONSTRAINT "uq_team_region_battlenet_id" UNIQUE ("region", "battlenet_id");
CREATE INDEX "ix_team_ladder_search" ON "team"("rating", "id");
VACUUM(FULL) "team";

CREATE INDEX "ix_team_state_timestamp" ON "team_state"("timestamp");

WITH player_character_filter AS
(
    SELECT DISTINCT team_member.player_character_id
    FROM team_member
    INNER JOIN team ON team_member.team_id = team.id
    INNER JOIN league_tier ON team.league_tier_id = league_tier.id
    INNER JOIN league ON league_tier.league_id = league.id
    WHERE league.queue_type <> 201 AND terran_games_played > 0
)
INSERT INTO player_character_stats
(player_character_id, queue_type, team_type, race, rating_max, league_max, games_played)
SELECT team_member.player_character_id, league.queue_type, league.team_type, 1,
MAX(team.rating), MAX(league.type),
SUM(terran_games_played)
FROM player_character_filter
INNER JOIN team_member USING(player_character_id)
INNER JOIN team ON team_member.team_id = team.id
INNER JOIN league_tier ON league_tier.id = team.league_tier_id
INNER JOIN league ON league.id = league_tier.league_id
WHERE league.queue_type <> 201
AND terran_games_played > 0
AND terran_games_played > COALESCE(protoss_games_played, 0)
AND terran_games_played > COALESCE(zerg_games_played, 0)
AND terran_games_played > COALESCE(random_games_played, 0)
GROUP BY league.queue_type, league.team_type, team_member.player_character_id
ON CONFLICT(player_character_id, COALESCE(race, -32768), queue_type, team_type) DO UPDATE SET
rating_max=excluded.rating_max,
league_max=excluded.league_max,
games_played=excluded.games_played
WHERE player_character_stats.games_played<>excluded.games_played;

WITH player_character_filter AS
(
    SELECT DISTINCT team_member.player_character_id
    FROM team_member
    INNER JOIN team ON team_member.team_id = team.id
    INNER JOIN league_tier ON team.league_tier_id = league_tier.id
    INNER JOIN league ON league_tier.league_id = league.id
    WHERE  league.queue_type <> 201 AND protoss_games_played > 0
)
INSERT INTO player_character_stats
(player_character_id, queue_type, team_type, race, rating_max, league_max, games_played)
SELECT team_member.player_character_id, league.queue_type, league.team_type, 2,
MAX(team.rating), MAX(league.type),
SUM(protoss_games_played)
FROM player_character_filter
INNER JOIN team_member USING(player_character_id)
INNER JOIN team ON team_member.team_id = team.id
INNER JOIN league_tier ON league_tier.id = team.league_tier_id
INNER JOIN league ON league.id = league_tier.league_id
WHERE league.queue_type <> 201
AND protoss_games_played > 0
AND protoss_games_played > COALESCE(terran_games_played, 0)
AND protoss_games_played > COALESCE(zerg_games_played, 0)
AND protoss_games_played > COALESCE(random_games_played, 0)
GROUP BY league.queue_type, league.team_type, team_member.player_character_id
ON CONFLICT(player_character_id, COALESCE(race, -32768), queue_type, team_type) DO UPDATE SET
rating_max=excluded.rating_max,
league_max=excluded.league_max,
games_played=excluded.games_played
WHERE player_character_stats.games_played<>excluded.games_played;

WITH player_character_filter AS
(
    SELECT DISTINCT team_member.player_character_id
    FROM team_member
    INNER JOIN team ON team_member.team_id = team.id
    INNER JOIN league_tier ON team.league_tier_id = league_tier.id
    INNER JOIN league ON league_tier.league_id = league.id
    WHERE league.queue_type <> 201 AND zerg_games_played > 0
)
INSERT INTO player_character_stats
(player_character_id, queue_type, team_type, race, rating_max, league_max, games_played)
SELECT team_member.player_character_id, league.queue_type, league.team_type, 3,
MAX(team.rating), MAX(league.type),
SUM(zerg_games_played)
FROM player_character_filter
INNER JOIN team_member USING(player_character_id)
INNER JOIN team ON team_member.team_id = team.id
INNER JOIN league_tier ON league_tier.id = team.league_tier_id
INNER JOIN league ON league.id = league_tier.league_id
WHERE league.queue_type <> 201
AND zerg_games_played > 0
AND zerg_games_played > COALESCE(terran_games_played, 0)
AND zerg_games_played > COALESCE(protoss_games_played, 0)
AND zerg_games_played > COALESCE(random_games_played, 0)
GROUP BY league.queue_type, league.team_type, team_member.player_character_id
ON CONFLICT(player_character_id, COALESCE(race, -32768), queue_type, team_type) DO UPDATE SET
rating_max=excluded.rating_max,
league_max=excluded.league_max,
games_played=excluded.games_played
WHERE player_character_stats.games_played<>excluded.games_played;

WITH player_character_filter AS
(
    SELECT DISTINCT team_member.player_character_id
    FROM team_member
    INNER JOIN team ON team_member.team_id = team.id
    INNER JOIN league_tier ON team.league_tier_id = league_tier.id
    INNER JOIN league ON league_tier.league_id = league.id
    WHERE league.queue_type <> 201 AND random_games_played > 0
)
INSERT INTO player_character_stats
(player_character_id, queue_type, team_type, race, rating_max, league_max, games_played)
SELECT team_member.player_character_id, league.queue_type, league.team_type, 4,
MAX(team.rating), MAX(league.type),
SUM(random_games_played)
FROM player_character_filter
INNER JOIN team_member USING(player_character_id)
INNER JOIN team ON team_member.team_id = team.id
INNER JOIN league_tier ON league_tier.id = team.league_tier_id
INNER JOIN league ON league.id = league_tier.league_id
WHERE league.queue_type <> 201
AND random_games_played > 0
AND random_games_played > COALESCE(protoss_games_played, 0)
AND random_games_played > COALESCE(zerg_games_played, 0)
AND random_games_played > COALESCE(terran_games_played, 0)
GROUP BY league.queue_type, league.team_type, team_member.player_character_id
ON CONFLICT(player_character_id, COALESCE(race, -32768), queue_type, team_type) DO UPDATE SET
rating_max=excluded.rating_max,
league_max=excluded.league_max,
games_played=excluded.games_played
WHERE player_character_stats.games_played<>excluded.games_played;

SET work_mem = '4MB';
