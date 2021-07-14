-- Copyright (C) 2021 Oleksandr Masniuk and contributors
-- SPDX-License-Identifier: AGPL-3.0-or-later
---

CREATE TABLE "clan"
(
    "id" SERIAL,
    "tag" TEXT NOT NULL,
    "region" SMALLINT NOT NULL,
    "name" TEXT,

    PRIMARY KEY("id"),

    CONSTRAINT "uq_clan_tag_region"
        UNIQUE ("tag", "region")
);

ALTER TABLE "player_character"
    ADD COLUMN "clan_id" INTEGER,
    ADD CONSTRAINT "fk_player_character_clan_id"
        FOREIGN KEY ("clan_id")
        REFERENCES "clan"("id")
        ON UPDATE CASCADE;

ALTER TABLE "team_state"
    ADD COLUMN "archived" BOOLEAN;
CREATE INDEX "ix_team_state_team_id_archived" ON "team_state"("team_id", "archived") WHERE "archived" = true;

VACUUM(ANALYZE) "team_state";

WITH 
team_filter AS 
( 
    SELECT DISTINCT(team_state.team_id)
    FROM team_state
), 
min_filter AS 
( 
    SELECT DISTINCT ON (team_state.team_id) 
    team_state.team_id, team_state.timestamp 
    FROM team_filter 
    INNER JOIN team_state USING(team_id)
    ORDER BY team_state.team_id ASC, team_state.rating ASC, team_state.timestamp ASC 
), 
max_filter AS 
( 
    SELECT DISTINCT ON (team_state.team_id) 
    team_state.team_id, team_state.timestamp 
    FROM team_filter 
    INNER JOIN team_state USING(team_id)
    ORDER BY team_state.team_id DESC, team_state.rating DESC, team_state.timestamp DESC 
)
UPDATE team_state 
SET archived = true
FROM min_filter
INNER JOIN max_filter USING(team_id)
WHERE team_state.team_id = min_filter.team_id
AND team_state.timestamp IN (min_filter.timestamp, max_filter.timestamp);

VACUUM(ANALYZE) "team_state";
