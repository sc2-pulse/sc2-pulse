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
percentage = (rank / teamCount) * 100;
RETURN
CASE
    WHEN gm = true AND rank <= 200 THEN row(0, 6, 0)::league_tier_type
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
