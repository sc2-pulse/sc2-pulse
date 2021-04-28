-- Copyright (C) 2021 Oleksandr Masniuk and contributors
-- SPDX-License-Identifier: AGPL-3.0-or-later
---

CREATE TABLE "season_state"
(
    "season_id" INTEGER NOT NULL,
    "timestamp" TIMESTAMP WITH TIME ZONE NOT NULL,
    "player_count" SMALLINT NOT NULL,
    "total_games_played" INTEGER NOT NULL,
    "games_played" SMALLINT,

    PRIMARY KEY("timestamp", "season_id"),

    CONSTRAINT "fk_season_state_season_id"
        FOREIGN KEY ("season_id")
        REFERENCES "season"("id")
        ON DELETE CASCADE ON UPDATE CASCADE
);

ALTER TABLE "season_state"
    RENAME COLUMN "timestamp" TO "period_start";
