-- Copyright (C) 2021 Oleksandr Masniuk
-- SPDX-License-Identifier: AGPL-3.0-or-later
---

ALTER TABLE "team_state"
    ADD COLUMN "secondary" BOOLEAN;

UPDATE "team_state"
SET "secondary" = true
FROM "team"
WHERE "team_state"."team_id" = "team"."id"
AND "team"."queue_type" <> 201;

CREATE INDEX "ix_team_state_secondary_timestamp" ON "team_state"("secondary", "timestamp") WHERE "secondary" = true;

ALTER TABLE "team_state"
    ADD COLUMN "global_rank" INTEGER,
    ADD COLUMN "global_team_count" INTEGER,
    ADD COLUMN "region_rank" INTEGER,
    ADD COLUMN "region_team_count" INTEGER;
    
WITH ranks AS
(
    SELECT id,
    RANK() OVER(PARTITION BY season, queue_type, team_type ORDER BY rating DESC) as global_rank,
    RANK() OVER(PARTITION BY season, queue_type, team_type, region ORDER BY rating DESC) as region_rank,
    RANK() OVER(PARTITION BY season, queue_type, team_type, league_type ORDER BY rating DESC) as league_rank
    FROM team
    WHERE season < 40
)
UPDATE team
set global_rank = ranks.global_rank,
region_rank = ranks.region_rank,
league_rank = ranks.league_rank
FROM ranks
WHERE team.id = ranks.id;

WITH ranks AS
(
    SELECT id,
    RANK() OVER(PARTITION BY season, queue_type, team_type ORDER BY rating DESC) as global_rank,
    RANK() OVER(PARTITION BY season, queue_type, team_type, region ORDER BY rating DESC) as region_rank,
    RANK() OVER(PARTITION BY season, queue_type, team_type, league_type ORDER BY rating DESC) as league_rank
    FROM team
    WHERE season >= 40
)
UPDATE team
set global_rank = ranks.global_rank,
region_rank = ranks.region_rank,
league_rank = ranks.league_rank
FROM ranks
WHERE team.id = ranks.id;

ALTER TABLE "team" SET (fillfactor = 50);
VACUUM FULL "team";

ALTER TABLE "player_character_stats" SET (fillfactor = 90);
VACUUM FULL "player_character_stats";

ALTER TABLE "match" SET (fillfactor = 90);
VACUUM FULL "match";

VACUUM FULL "account";
VACUUM FULL "team_state";

VACUUM(ANALYZE);

