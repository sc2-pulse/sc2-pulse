-- Copyright (C) 2020 Oleksandr Masniuk and contributors
-- SPDX-License-Identifier: AGPL-3.0-or-later
---

CREATE TABLE "pro_player"
(
    "id" BIGSERIAL,
    "revealed_id" bytea NOT NULL,
    "nickname" VARCHAR(50) NOT NULL,
    "name" VARCHAR(50) NOT NULL,
    "country" CHAR(2),
    "team" VARCHAR(50),
    "birthday" DATE,
    "earnings" INTEGER,
    "updated" TIMESTAMP NOT NULL DEFAULT NOW(),

    PRIMARY KEY ("id"),

    CONSTRAINT "uq_pro_player_revealed_id"
        UNIQUE("revealed_id")
);

CREATE INDEX "pro_player_updated" ON "pro_player"("updated");

CREATE TABLE "social_media_link"
(
    "pro_player_id" BIGINT NOT NULL,
    "type" SMALLINT NOT NULL,
    "url" VARCHAR(255) NOT NULL,
    "updated" TIMESTAMP NOT NULL DEFAULT NOW(),

    PRIMARY KEY("pro_player_id", "type"),

    CONSTRAINT "fk_social_media_link_pro_player_id"
        FOREIGN KEY ("pro_player_id")
        REFERENCES "pro_player"("id")
        ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE INDEX "social_media_link_updated" ON "social_media_link"("updated");

CREATE TABLE "pro_player_account"
(
    "pro_player_id" BIGINT NOT NULL,
    "account_id" BIGINT NOT NULL,
    "updated" TIMESTAMP NOT NULL DEFAULT NOW(),

    PRIMARY KEY("pro_player_id", "account_id"),

    CONSTRAINT "fk_pro_player_account_pro_player_id"
        FOREIGN KEY ("pro_player_id")
        REFERENCES "pro_player"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "fk_pro_player_account_account_id"
        FOREIGN KEY ("account_id")
        REFERENCES "account"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT "uq_pro_player_account_account_id"
        UNIQUE("account_id")

);

CREATE INDEX "pro_player_account_updated" ON "pro_player_account"("updated");
