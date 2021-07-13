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

CREATE TABLE "clan_member"
(
    "clan_id" INTEGER NOT NULL,
    "player_character_id" BIGINT NOT NULL,
    "updated" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    PRIMARY KEY("player_character_id"),

    CONSTRAINT "fk_clan_member_clan_id"
        FOREIGN KEY ("clan_id")
        REFERENCES "clan"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "fk_clan_member_player_character_id"
        FOREIGN KEY ("player_character_id")
        REFERENCES "player_character"("id")
        ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE INDEX "ix_clan_member_clan_id" ON "clan_member"("clan_id");
CREATE INDEX "ix_clan_member_updated" ON "clan_member"("updated");

DROP TABLE "clan_member";

ALTER TABLE "player_character"
    ADD COLUMN "clan_id" INTEGER,
    ADD CONSTRAINT "fk_player_character_clan_id"
        FOREIGN KEY ("clan_id")
        REFERENCES "clan"("id")
        ON UPDATE CASCADE;

ALTER TABLE "team_state"
    ADD COLUMN "archived" BOOLEAN;
    
WITH 
team_filter AS 
( 
    SELECT DISTINCT(team_state.team_id) 
    FROM team_state
), 
min_max_filter AS 
( 
    SELECT team_state.team_id, 
    MIN(team_state.rating) AS rating_min, 
    MAX(team_state.rating) AS rating_max 
    FROM team_filter 
    INNER JOIN team_state USING(team_id) 
    WHERE archived = true 
    GROUP BY team_state.team_id 
), 
all_filter AS 
( 
    SELECT team_filter.team_id, min_max_filter.rating_min, min_max_filter.rating_max 
    FROM team_filter 
    LEFT JOIN min_max_filter USING(team_id) 
) 
UPDATE team_state 
SET archived = true 
FROM all_filter 
WHERE team_state.team_id = all_filter.team_id 
AND (team_state.rating > COALESCE(all_filter.rating_max, -1) 
    OR team_state.rating < all_filter.rating_min);
    
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
    WHERE archived = true 
    ORDER BY team_state.team_id ASC, team_state.rating ASC, team_state.timestamp ASC 
), 
max_filter AS 
( 
    SELECT DISTINCT ON (team_state.team_id) 
    team_state.team_id, team_state.timestamp 
    FROM team_filter 
    INNER JOIN team_state USING(team_id) 
    WHERE archived = true 
    ORDER BY team_state.team_id DESC, team_state.rating DESC, team_state.timestamp DESC 
)
UPDATE team_state 
SET archived = null 
FROM min_filter
INNER JOIN max_filter USING(team_id)
WHERE team_state.team_id = min_filter.team_id
AND team_state.timestamp != min_filter.timestamp
AND team_state.timestamp != max_filter.timestamp
AND team_state.archived = true;

