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
