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
    "region_league_team_count" INTEGER,

    PRIMARY KEY("id"),

    CONSTRAINT "fk_population_state_league_id"
        FOREIGN KEY ("league_id")
        REFERENCES "league"("id")
        ON DELETE CASCADE ON UPDATE CASCADE
);

ALTER TABLE "team_state"
    ADD COLUMN population_state_id INTEGER;

SET work_mem = '32MB';

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

SET work_mem = '4MB';

ALTER TABLE "team_state"
    ADD CONSTRAINT "fk_team_state_population_state_id"
        FOREIGN KEY ("population_state_id")
        REFERENCES "population_state"("id")
        ON DELETE SET NULL ON UPDATE CASCADE,
    DROP COLUMN "global_team_count",
    DROP COLUMN "region_team_count";

ALTER TABLE "team_state"
    ADD COLUMN "wins" SMALLINT;

