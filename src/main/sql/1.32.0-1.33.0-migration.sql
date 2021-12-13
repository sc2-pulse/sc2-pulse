-- Copyright (C) 2021 Oleksandr Masniuk
-- SPDX-License-Identifier: AGPL-3.0-or-later
---

ALTER TABLE "map_stats"
    ALTER COLUMN "games" TYPE INTEGER,
    ALTER COLUMN "games_with_duration" TYPE INTEGER,
    ALTER COLUMN "wins" TYPE INTEGER,
    ALTER COLUMN "losses" TYPE INTEGER,
    ALTER COLUMN "ties" TYPE INTEGER;
