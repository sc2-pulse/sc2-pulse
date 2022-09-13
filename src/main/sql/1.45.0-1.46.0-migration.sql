-- Copyright (C) 2022 Oleksandr Masniuk
-- SPDX-License-Identifier: AGPL-3.0-or-later
---

ALTER TABLE "account"
    ADD COLUMN "battle_tag_last_season" SMALLINT NOT NULL DEFAULT 0;

CREATE TABLE "clan_member"
(
    "player_character_id" BIGINT NOT NULL,
    "clan_id" INTEGER NOT NULL,

    PRIMARY KEY("player_character_id"),

    CONSTRAINT "fk_clan_member_player_character_id"
        FOREIGN KEY ("player_character_id")
        REFERENCES "player_character"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "fk_clan_member_clan_id"
        FOREIGN KEY ("clan_id")
        REFERENCES "clan"("id")
        ON DELETE CASCADE ON UPDATE CASCADE
);

INSERT INTO "clan_member"("player_character_id", "clan_id")
SELECT "id", "clan_id"
FROM "player_character"
WHERE "clan_id" IS NOT NULL;

VACUUM(ANALYZE) "clan_member";

CREATE INDEX "ix_clan_member_clan_id" ON "clan_member"("clan_id");

ALTER TABLE "player_character"
DROP COLUMN "clan_id";
