-- Copyright (C) 2021 Oleksandr Masniuk and contributors
-- SPDX-License-Identifier: AGPL-3.0-or-later
---

CREATE INDEX "ix_player_character_clan_id" ON "player_character"("clan_id") WHERE "clan_id" IS NOT NULL;
