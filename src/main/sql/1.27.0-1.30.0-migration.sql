-- Copyright (C) 2021 Oleksandr Masniuk
-- SPDX-License-Identifier: AGPL-3.0-or-later
---

CREATE TABLE "map"
(
    "id" SERIAL,
    "name" TEXT NOT NULL,

    PRIMARY KEY ("id"),

    CONSTRAINT "uq_map_name"
        UNIQUE("name")
);

INSERT INTO "map"("name")
SELECT DISTINCT("map") FROM "match";

ALTER TABLE "match"
    ADD COLUMN "map_id" INTEGER NOT NULL DEFAULT 0;

UPDATE "match"
SET "map_id" = "map"."id"
FROM "map"
WHERE "map"."name" = "match"."map";

ALTER TABLE "match"
    ALTER COLUMN "map_id" DROP DEFAULT,
    ADD CONSTRAINT "fk_match_map_id"
        FOREIGN KEY ("map_id")
        REFERENCES "map"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    DROP CONSTRAINT "uq_match_date_type_map_region",
    ADD CONSTRAINT "uq_match_date_type_map_id_region"
        UNIQUE("date", "type", "map_id", "region"),
    DROP COLUMN "map";

ALTER TABLE "match"
    ADD COLUMN "duration" SMALLINT;

WITH 
match_filter AS
(
    SELECT player_character_id, date
    FROM match
    INNER JOIN match_participant ON match.id = match_participant.match_id
    WHERE match.date >= NOW() - INTERVAL '450 minutes'
),
match_duration AS
(
    SELECT id, EXTRACT(EPOCH FROM (match.date - MAX(match_duration.date))) AS duration
    FROM match
    INNER JOIN match_participant ON match.id = match_participant.match_id
    JOIN LATERAL
    (
        SELECT match_filter.date
        FROM match_filter
        WHERE match_filter.player_character_id = match_participant.player_character_id
        AND match_filter.date < match.date
        ORDER BY match_filter.date DESC
        LIMIT 1
    ) match_duration ON true
    WHERE match.date >= NOW() - INTERVAL '360 minutes'
    GROUP BY match.id
)
UPDATE match
SET duration = match_duration.duration
FROM match_duration
WHERE match.id = match_duration.id
AND match_duration.duration BETWEEN 1 AND 5400
AND (match.duration IS NULL OR match.duration > match_duration.duration);

CREATE TABLE "map_stats"
(
    "id" SERIAL,
    "map_id" INTEGER,
    "league_id" INTEGER NOT NULL,
    "race" SMALLINT NOT NULL,
    "versus_race" SMALLINT NOT NULL,
    "games" SMALLINT NOT NULL,
    "games_with_duration" SMALLINT NOT NULL,
    "wins" SMALLINT NOT NULL,
    "losses" SMALLINT NOT NULL,
    "ties" SMALLINT NOT NULL,
    "duration" INTEGER NOT NULL,

    PRIMARY KEY("id"),

    CONSTRAINT "fk_map_stats_map_id"
        FOREIGN KEY ("map_id")
        REFERENCES "map"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "fk_map_stats_league_id"
        FOREIGN KEY ("league_id")
        REFERENCES "league"("id")
        ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE UNIQUE INDEX "uq_map_stats_league_id_map_id_race_versus_race"
    ON "map_stats"("league_id", COALESCE("map_id", -1), "race", "versus_race");

CREATE OR REPLACE FUNCTION get_top_percentage_league_lotv(rank INTEGER, teamCount DOUBLE PRECISION, gm BOOLEAN)
RETURNS SMALLINT
AS
'
DECLARE percentage DOUBLE PRECISION;
BEGIN
percentage = (rank / teamCount) * 100;
RETURN
CASE
    WHEN gm = true AND rank <= 200 THEN 6
    WHEN percentage <= 4 THEN 5
    WHEN percentage <= 27 THEN 4
    WHEN percentage <= 50 THEN 3
    WHEN percentage <= 73 THEN 2
    WHEN percentage <= 96 THEN 1
    ELSE 0
END;
END
'
LANGUAGE plpgsql;
