-- Copyright (C) 2021 Oleksandr Masniuk and contributors
-- SPDX-License-Identifier: AGPL-3.0-or-later
---

ALTER TABLE "match_participant"
    ADD COLUMN "team_id" BIGINT,
    ADD COLUMN "team_state_timestamp" TIMESTAMP WITH TIME ZONE,
    ADD CONSTRAINT "fk_match_participant_team_id"
        FOREIGN KEY ("team_id")
        REFERENCES "team"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    ADD CONSTRAINT "fk_match_participant_team_state_uid"
        FOREIGN KEY ("team_id", "team_state_timestamp")
        REFERENCES "team_state"("team_id", "timestamp")
        ON DELETE CASCADE ON UPDATE CASCADE;

DELETE FROM "match";
ALTER TABLE "match"
    ADD COLUMN "region" SMALLINT NOT NULL,
    DROP CONSTRAINT "uq_match_date_type_map",
    ADD CONSTRAINT "uq_match_date_type_map_region"
        UNIQUE("date", "type", "map", "region");