-- Copyright (C) 2022 Oleksandr Masniuk
-- SPDX-License-Identifier: AGPL-3.0-or-later
---

CREATE TABLE "twitch_user"
(
    "id" BIGINT NOT NULL,
    "login" TEXT NOT NULL,

    PRIMARY KEY ("id")
);

CREATE INDEX "ix_twitch_user_login" ON "twitch_user"(LOWER("login"));

CREATE TABLE "twitch_video"
(
    "id" BIGINT NOT NULL,
    "twitch_user_id" BIGINT NOT NULL,
    "url" TEXT NOT NULL,
    "begin" TIMESTAMP WITH TIME ZONE NOT NULL,
    "end" TIMESTAMP WITH TIME ZONE NOT NULL,

    PRIMARY KEY ("id"),

    CONSTRAINT "fk_twitch_video_twitch_user_id"
        FOREIGN KEY ("twitch_user_id")
        REFERENCES "twitch_user"("id")
        ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE INDEX "ix_twitch_video_twitch_user_id_begin_end" ON "twitch_video"("twitch_user_id", "begin", "end");

ALTER TABLE "pro_player"
    ADD COLUMN "twitch_user_id" BIGINT,
    ADD CONSTRAINT "fk_pro_player_twitch_user_id"
        FOREIGN KEY ("twitch_user_id")
        REFERENCES "twitch_user"("id")
        ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE "match_participant"
    ADD COLUMN "twitch_video_id" BIGINT,
    ADD CONSTRAINT "fk_match_participant_twitch_video_id"
        FOREIGN KEY ("twitch_video_id")
        REFERENCES "twitch_video"("id")
        ON DELETE SET NULL ON UPDATE CASCADE,
    ADD COLUMN "twitch_video_offset" INTEGER;
