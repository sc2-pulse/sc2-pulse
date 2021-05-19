-- Copyright (C) 2021 Oleksandr Masniuk and contributors
-- SPDX-License-Identifier: AGPL-3.0-or-later
---

ALTER TABLE "team"
    DROP CONSTRAINT "uq_team_season_region_queue_type_legacy_id",
    ADD CONSTRAINT "uq_team_queue_type_region_legacy_id_season"
            UNIQUE ("queue_type", "region", "legacy_id", "season");

ALTER TABLE "team"
    DROP CONSTRAINT "uq_team_queue_type_region_legacy_id_season";

CREATE UNIQUE INDEX "uq_team_legacy_uid_season" ON "team"(CAST("queue_type"::text || "region"::text || "legacy_id"::text AS numeric), "season");

DROP INDEX "uq_team_legacy_uid_season";
ALTER TABLE "team"
    ADD CONSTRAINT "uq_team_queue_type_region_legacy_id_season"
        UNIQUE ("queue_type", "region", "legacy_id", "season");
