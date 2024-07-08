CREATE TYPE league_tier_type AS
(
    id SMALLINT,
    league SMALLINT,
    tier SMALLINT
);

CREATE OR REPLACE FUNCTION get_top_percentage_league_tier_lotv(rank INTEGER, teamCount DOUBLE PRECISION, gm BOOLEAN)
RETURNS league_tier_type
AS
'
DECLARE percentage DOUBLE PRECISION;
BEGIN
IF gm = true
THEN
    IF rank <= 200
    THEN
        RETURN row(0, 6, 0)::league_tier_type;
    ELSE
        rank = rank - 200;
        teamCount = teamCount - 200;
    END IF;
END IF;
percentage = (rank / teamCount) * 100;
RETURN
CASE
    WHEN percentage <= 1.333 THEN row(1, 5, 0)::league_tier_type
    WHEN percentage <= 2.666 THEN row(2, 5, 1)::league_tier_type
    WHEN percentage <= 4 THEN row(3, 5, 2)::league_tier_type
    WHEN percentage <= 11.666 THEN row(4, 4, 0)::league_tier_type
    WHEN percentage <= 19.333 THEN row(5, 4, 1)::league_tier_type
    WHEN percentage <= 27 THEN row(6, 4, 2)::league_tier_type
    WHEN percentage <= 34.666 THEN row(7, 3, 0)::league_tier_type
    WHEN percentage <= 42.333 THEN row(8, 3, 1)::league_tier_type
    WHEN percentage <= 50 THEN row(9, 3, 2)::league_tier_type
    WHEN percentage <= 57.666 THEN row(10, 2, 0)::league_tier_type
    WHEN percentage <= 65.333 THEN row(11, 2, 1)::league_tier_type
    WHEN percentage <= 73 THEN row(12, 2, 2)::league_tier_type
    WHEN percentage <= 80.666 THEN row(13, 1, 0)::league_tier_type
    WHEN percentage <= 88.333 THEN row(14, 1, 1)::league_tier_type
    WHEN percentage <= 96 THEN row(15, 1, 2)::league_tier_type
    WHEN percentage <= 97.333 THEN row(16, 0, 0)::league_tier_type
    WHEN percentage <= 98.666 THEN row(17, 0, 1)::league_tier_type
    ELSE row(18, 0, 2)::league_tier_type
END;
END
'
LANGUAGE plpgsql;

CREATE TABLE "map_stats_film_spec"
(
    "id" SMALLINT NOT NULL GENERATED ALWAYS AS IDENTITY,
    "race" SMALLINT NOT NULL,
    "versus_race" SMALLINT NOT NULL,
    "frame_duration" SMALLINT NOT NULL,

    PRIMARY KEY("id"),

    CONSTRAINT "uq_map_stats_film_spec"
            UNIQUE("race", "versus_race", "frame_duration")
);

CREATE TABLE "map_stats_film"
(
    "id" SERIAL,
    "map_id" INTEGER NOT NULL,
    "league_tier_id" INTEGER NOT NULL,
    "map_stats_film_spec_id" SMALLINT NOT NULL,

    PRIMARY KEY("id"),

    CONSTRAINT "uq_map_stats_film"
        UNIQUE("league_tier_id", "map_id", "map_stats_film_spec_id"),

    CONSTRAINT "fk_map_stats_film_map_id"
        FOREIGN KEY ("map_id")
        REFERENCES "map"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "fk_map_stats_film_league_tier_id"
        FOREIGN KEY ("league_tier_id")
        REFERENCES "league_tier"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "fk_map_stats_film_map_stats_film_spec_id"
        FOREIGN KEY ("map_stats_film_spec_id")
        REFERENCES "map_stats_film_spec"("id")
        ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE "map_stats_film_frame"
(
    "map_stats_film_id" INTEGER NOT NULL,
    "number" SMALLINT NOT NULL,
    "games" SMALLINT NOT NULL,
    "wins" SMALLINT NOT NULL,

    PRIMARY KEY("map_stats_film_id", "number"),

    CONSTRAINT "fk_map_stats_film_frame_map_stats_film_id"
        FOREIGN KEY ("map_stats_film_id")
        REFERENCES "map_stats_film"("id")
        ON DELETE CASCADE ON UPDATE CASCADE
);

ALTER TABLE "team" SET (fillfactor = 100);
VACUUM FULL "team";

CREATE UNIQUE INDEX "uq_map_stats_film_frame_map_stats_film_id_number"
    ON "map_stats_film_frame"("map_stats_film_id", COALESCE("number", -1));
ALTER TABLE "map_stats_film_frame"
    DROP CONSTRAINT "map_stats_film_frame_pkey",
    ALTER COLUMN "number" DROP NOT NULL;

ALTER TABLE "map_stats_film"
    ADD COLUMN "cross_tier" BOOLEAN NOT NULL DEFAULT false,
    DROP CONSTRAINT "uq_map_stats_film",
    ADD CONSTRAINT "uq_map_stats_film"
        UNIQUE("league_tier_id", "map_id", "map_stats_film_spec_id", "cross_tier");
ALTER TABLE "map_stats_film"
    ALTER COLUMN "cross_tier" DROP DEFAULT;
