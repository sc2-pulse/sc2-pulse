-- Copyright (C) 2020 Oleksandr Masniuk and contributors
-- SPDX-License-Identifier: AGPL-3.0-or-later
---

ALTER TABLE "team" ALTER COLUMN "battlenet_id" DROP NOT NULL;

CREATE TABLE "team_state"
(
    "team_id" BIGINT NOT NULL,
    "timestamp" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    "division_id" BIGINT NOT NULL,
    "games" SMALLINT NOT NULL,
    "rating" SMALLINT NOT NULL,

    PRIMARY KEY ("team_id", "timestamp"),

    CONSTRAINT "fk_team_state_team_id"
        FOREIGN KEY ("team_id")
        REFERENCES "team"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "fk_team_state_division_id"
        FOREIGN KEY ("division_id")
        REFERENCES "division"("id")
        ON DELETE CASCADE ON UPDATE CASCADE
);

ALTER TABLE "player_character"
DROP CONSTRAINT "uq_player_character_region_battlenet_id",
ADD CONSTRAINT "uq_player_character_region_realm_battlenet_id" UNIQUE ("region", "realm", "battlenet_id");
