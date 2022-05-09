-- Copyright (C) 2022 Oleksandr Masniuk
-- SPDX-License-Identifier: AGPL-3.0-or-later
---

CREATE INDEX "ix_clan_tag" ON "clan"(LOWER("tag") text_pattern_ops);
