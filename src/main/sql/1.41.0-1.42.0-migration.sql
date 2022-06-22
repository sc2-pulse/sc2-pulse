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

CREATE INDEX "ix_match_participant_twitch_video_id"
    ON "match_participant"("twitch_video_id")
    WHERE "twitch_video_id" IS NOT NULL;

ALTER TABLE "social_media_link"
    ADD COLUMN "protected" BOOLEAN;

ALTER TABLE "pro_player_account"
    ADD COLUMN "protected" BOOLEAN;

UPDATE social_media_link SET updated = NOW() + INTERVAL '100 years';
UPDATE pro_player SET updated = NOW() + INTERVAL '100 years';
UPDATE pro_player_account SET updated = NOW() + INTERVAL '100 years';

ALTER TABLE "twitch_user"
    ADD COLUMN "sub_only_vod" BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE "match"
    ADD COLUMN "vod" BOOLEAN,
    ADD COLUMN "sub_only_vod" BOOLEAN,
    ADD COLUMN "rating_min" INTEGER,
    ADD COLUMN "rating_max" INTEGER,
    ADD COLUMN "race" TEXT;

CREATE INDEX "ix_match_vod_search" ON "match"("date", "type", "map_id", "vod", "sub_only_vod", "race" text_pattern_ops, "rating_min", "duration", "rating_max")
    WHERE "vod" = true;
