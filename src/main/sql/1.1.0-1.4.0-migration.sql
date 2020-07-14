-- Copyright (C) 2020 Oleksandr Masniuk and contributors
-- SPDX-License-Identifier: AGPL-3.0-or-later
---

CREATE TABLE "queue_stats"
(
    "id" BIGSERIAL,
    "season" BIGINT NOT NULL,
    "queue_type" SMALLINT NOT NULL,
    "team_type" SMALLINT NOT NULL,
    "player_base" BIGINT NOT NULL,

    PRIMARY KEY ("id"),

    CONSTRAINT "uq_queue_stats_queue_type_team_type_season"
        UNIQUE ("queue_type", "team_type", "season")
);
