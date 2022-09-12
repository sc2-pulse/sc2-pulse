-- Copyright (C) 2022 Oleksandr Masniuk
-- SPDX-License-Identifier: AGPL-3.0-or-later
---

ALTER TABLE "account"
    ADD COLUMN "battle_tag_last_season" SMALLINT NOT NULL DEFAULT 0;