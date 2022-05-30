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
