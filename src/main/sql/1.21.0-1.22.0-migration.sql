-- Copyright (C) 2021 Oleksandr Masniuk
-- SPDX-License-Identifier: AGPL-3.0-or-later
---

ALTER TABLE "team"
    ALTER COLUMN "tier_type" DROP NOT NULL;

UPDATE "team"
SET "tier_type" = NULL
WHERE "season" IN (46, 47, 48);
