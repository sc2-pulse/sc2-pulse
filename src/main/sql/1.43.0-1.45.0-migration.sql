-- Copyright (C) 2022 Oleksandr Masniuk
-- SPDX-License-Identifier: AGPL-3.0-or-later
---

CREATE TABLE "discord_user"
(
    "id" BIGINT NOT NULL,
    "name" TEXT NOT NULL,
    "discriminator" SMALLINT NOT NULL,

    PRIMARY KEY("id"),

    CONSTRAINT "uq_discord_user_name_discriminator"
        UNIQUE("name", "discriminator")
);

CREATE TABLE "account_discord_user"
(
    "account_id" BIGINT NOT NULL,
    "discord_user_id" BIGINT NOT NULL,

    PRIMARY KEY("account_id"),

    CONSTRAINT "fk_account_discord_user_account_id"
        FOREIGN KEY ("account_id")
        REFERENCES "account"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "fk_account_discord_user_discord_user_id"
        FOREIGN KEY ("discord_user_id")
        REFERENCES "discord_user"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT "uq_account_discord_user_discord_user_id"
        UNIQUE("discord_user_id")
);
