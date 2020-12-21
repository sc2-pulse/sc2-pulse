-- Copyright (C) 2020 Oleksandr Masniuk and contributors
-- SPDX-License-Identifier: AGPL-3.0-or-later
---

CREATE INDEX "ix_pro_player_account_pro_player_id" ON "pro_player_account"("pro_player_id");
CREATE INDEX "ix_pro_team_member_pro_team_id" ON "pro_team_member"("pro_team_id");

ALTER TABLE "account" ALTER COLUMN "updated" TYPE TIMESTAMP WITH TIME ZONE;
ALTER TABLE "player_character_stats" ALTER COLUMN "updated" TYPE TIMESTAMP WITH TIME ZONE USING "updated"::timestamptz;
ALTER TABLE "pro_player" ALTER COLUMN "updated" TYPE TIMESTAMP WITH TIME ZONE;
ALTER TABLE "social_media_link" ALTER COLUMN "updated" TYPE TIMESTAMP WITH TIME ZONE;
ALTER TABLE "pro_player_account" ALTER COLUMN "updated" TYPE TIMESTAMP WITH TIME ZONE;
ALTER TABLE "pro_team" ALTER COLUMN "updated" TYPE TIMESTAMP WITH TIME ZONE;
ALTER TABLE "pro_team_member" ALTER COLUMN "updated" TYPE TIMESTAMP WITH TIME ZONE;

CREATE TABLE "match"
(
    "id" BIGSERIAL,
    "date" TIMESTAMP WITH TIME ZONE NOT NULL,
    "type" SMALLINT NOT NULL,
    "map" VARCHAR(100) NOT NULL,
    "updated" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    PRIMARY KEY ("id"),

    CONSTRAINT "uq_match_date_type_map"
        UNIQUE("date", "type", "map")
);

CREATE INDEX "ix_match_updated" ON "match"("updated");

CREATE TABLE "match_participant"
(
    "match_id" BIGINT NOT NULL,
    "player_character_id" BIGINT NOT NULL,
    "decision" SMALLINT NOT NULL,

    PRIMARY KEY ("match_id", "player_character_id"),

    CONSTRAINT "fk_match_participant_match_id"
        FOREIGN KEY ("match_id")
        REFERENCES "match"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "fk_match_participant_player_character_id"
        FOREIGN KEY ("player_character_id")
        REFERENCES "player_character"("id")
        ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE INDEX "ix_match_participant_player_character_id" ON "match_participant"("player_character_id");
