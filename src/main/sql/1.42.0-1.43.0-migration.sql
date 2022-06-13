-- Copyright (C) 2022 Oleksandr Masniuk
-- SPDX-License-Identifier: AGPL-3.0-or-later
---

ALTER TABLE team
    ALTER COLUMN "global_rank" DROP NOT NULL,
    ALTER COLUMN "global_rank" DROP DEFAULT,
    ALTER COLUMN "region_rank" DROP NOT NULL,
    ALTER COLUMN "region_rank" DROP DEFAULT,
    ALTER COLUMN "league_rank" DROP NOT NULL,
    ALTER COLUMN "league_rank" DROP DEFAULT;

BEGIN;

UPDATE team
SET global_rank = null
WHERE global_rank = 2147483647;

UPDATE team
SET region_rank = null
WHERE region_rank = 2147483647;

UPDATE team
SET league_rank = null
WHERE league_rank = 2147483647;

COMMIT;

CREATE TABLE "population_state"
(
    "id" SERIAL,
    "league_id" INTEGER NOT NULL,
    "global_team_count" INTEGER NOT NULL,
    "region_team_count" INTEGER NOT NULL,
    "league_team_count" INTEGER,

    PRIMARY KEY("id"),

    CONSTRAINT "fk_population_state_league_id"
        FOREIGN KEY ("league_id")
        REFERENCES "league"("id")
        ON DELETE CASCADE ON UPDATE CASCADE
);

ALTER TABLE "team_state"
    ADD COLUMN population_state_id INTEGER;

SET work_mem = '32MB';

ALTER TABLE "team"
    ADD COLUMN "population_state_id" INTEGER;

WITH global_team_count AS
(
    SELECT season, queue_type, team_type, COUNT(*) AS count
    FROM team
    GROUP BY season, queue_type, team_type
),
region_team_count AS
(
    SELECT season, region, queue_type, team_type, COUNT(*) AS count
    FROM team
    GROUP BY season, region, queue_type, team_type
),
league_team_count AS
(
    SELECT season, region, queue_type, team_type, league_id, COUNT(*) AS count
    FROM team
    INNER JOIN division ON team.division_id = division.id
    INNER JOIN league_tier ON division.league_tier_id = league_tier.id
    GROUP BY season, region, queue_type, team_type, league_id
)
INSERT INTO population_state(league_id, global_team_count, region_team_count, league_team_count)
SELECT league_team_count.league_id,
global_team_count.count,
region_team_count.count,
league_team_count.count
FROM league_team_count
INNER JOIN region_team_count USING(season, region, queue_type, team_type)
INNER JOIN global_team_count USING(season, queue_type, team_type);

VACUUM(ANALYZE) population_state;
CREATE INDEX "ix_population_state_temporary" ON "population_state"("league_id", "id");

DO
$do$
BEGIN
    FOR i IN 0..60 LOOP
        UPDATE team
        SET population_state_id = population_state.id
        FROM team t
        INNER JOIN division ON t.division_id = division.id
        INNER JOIN league_tier ON division.league_tier_id = league_tier.id
        INNER JOIN population_state USING(league_id)
        WHERE team.id = t.id
        AND team.season = i;
    END LOOP;
END
$do$;

ALTER TABLE "team"
    ADD CONSTRAINT "fk_team_population_state_id"
        FOREIGN KEY ("population_state_id")
        REFERENCES "population_state"("id")
        ON DELETE SET NULL ON UPDATE CASCADE;
DROP INDEX "ix_population_state_temporary";

INSERT INTO population_state(league_id, global_team_count, region_team_count)
SELECT league_id, global_team_count, region_team_count
FROM team_state
INNER JOIN division ON team_state.division_id = division.id
INNER JOIN league_tier ON division.league_tier_id = league_tier.id
WHERE global_team_count IS NOT NULL
AND region_team_count IS NOT NULL
GROUP BY league_id, global_team_count, region_team_count;

--updates team states between 2020-01-01 and 2023-01-01 in batches
DO
$do$
BEGIN
   FOR i IN 0..1080 LOOP
      UPDATE team_state
      SET population_state_id = population_state.id
      FROM team_state tsf
      INNER JOIN division ON tsf.division_id = division.id
      INNER JOIN league_tier ON division.league_tier_id = league_tier.id
      INNER JOIN population_state ON population_state.league_id = league_tier.league_id
          AND population_state.global_team_count = tsf.global_team_count
          AND population_state.region_team_count = tsf.region_team_count
      WHERE team_state.team_id = tsf.team_id
      AND team_state."timestamp" = tsf."timestamp"
      AND tsf.global_team_count IS NOT NULL
      AND tsf.region_team_count IS NOT NULL
      AND tsf."timestamp" >= date '2020-01-01' + i AND tsf."timestamp" < date '2020-01-01' + (i + 1);
   END LOOP;
END
$do$;

ALTER TABLE "team_state"
    ADD CONSTRAINT "fk_team_state_population_state_id"
        FOREIGN KEY ("population_state_id")
        REFERENCES "population_state"("id")
        ON DELETE SET NULL ON UPDATE CASCADE,
    DROP COLUMN "global_team_count",
    DROP COLUMN "region_team_count";

ALTER TABLE "team_state"
    ADD COLUMN "wins" SMALLINT;

--change global league rank to regional league rank
DO
$do$
BEGIN
   FOR i IN 0..60 LOOP
        WITH
        cheaters AS
        (
            SELECT DISTINCT(team_id)
            FROM team
            INNER JOIN team_member ON team.id = team_member.team_id
            INNER JOIN player_character_report AS confirmed_cheater_report
                ON team_member.player_character_id = confirmed_cheater_report.player_character_id
                AND confirmed_cheater_report.type = 1
                AND confirmed_cheater_report.status = true
            WHERE team.season = i
        ),
        ranks AS
        (
            SELECT id,
            RANK() OVER(PARTITION BY queue_type, team_type, region, league_type ORDER BY rating DESC) as league_rank
            FROM team
            WHERE season = i
            AND id NOT IN(SELECT team_id FROM cheaters)
        ),
        cheater_update AS
        (
            UPDATE team
            SET global_rank = null,
            region_rank = null,
            league_rank = null
            FROM cheaters
            WHERE team.id = cheaters.team_id
        )
        UPDATE team
        set league_rank = ranks.league_rank
        FROM ranks
        WHERE team.id = ranks.id
    END LOOP;
END
$do$;

ALTER TABLE "team_state"
    ADD COLUMN "league_rank" INTEGER;

SET work_mem = '4MB';
