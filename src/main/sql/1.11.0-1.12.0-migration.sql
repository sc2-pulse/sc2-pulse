-- Copyright (C) 2021 Oleksandr Masniuk and contributors
-- SPDX-License-Identifier: AGPL-3.0-or-later
---

ALTER TABLE "account" ALTER COLUMN "battle_tag" TYPE TEXT;
ALTER TABLE "player_character" ALTER COLUMN "name" TYPE TEXT;
ALTER TABLE "pro_player"
    ALTER COLUMN "nickname" TYPE TEXT,
    ALTER COLUMN "name" TYPE TEXT,
    ALTER COLUMN "team" TYPE TEXT;
ALTER TABLE "social_media_link" ALTER COLUMN "url" TYPE TEXT;
ALTER TABLE "pro_team"
    ALTER COLUMN "name" TYPE TEXT,
    ALTER COLUMN "short_name" TYPE TEXT;
ALTER TABLE "match" ALTER COLUMN "map" TYPE TEXT;
