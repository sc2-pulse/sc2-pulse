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
match_duration AS
(
    SELECT id, EXTRACT(EPOCH FROM (match.date - match_duration.date)) AS duration
    FROM match
    JOIN LATERAL
    (
        SELECT mb.date
        FROM match_participant
        INNER JOIN match_participant mpb USING(player_character_id)
        INNER JOIN match mb ON mpb.match_id = mb.id
        WHERE match_participant.match_id = match.id
        AND mb.date < match.date
        ORDER BY mb.date DESC
        LIMIT 1
    ) match_duration ON true
)
UPDATE match
SET duration = match_duration.duration
FROM match_duration
WHERE match.id = match_duration.id
AND match_duration.duration BETWEEN 1 AND 5400
