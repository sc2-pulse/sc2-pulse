-- Copyright (C) 2021 Oleksandr Masniuk
-- SPDX-License-Identifier: AGPL-3.0-or-later
---

DROP FUNCTION get_player_character_summary;
DROP TYPE player_character_summary;

ALTER TABLE "clan"
    ADD COLUMN "members" SMALLINT,
    ADD COLUMN "active_members" SMALLINT,
    ADD COLUMN "avg_rating" SMALLINT,
    ADD COLUMN "avg_league_type" SMALLINT,
    ADD COLUMN "games" INTEGER;

CREATE INDEX "ix_clan_search_active_members"
    ON "clan"("active_members", "id", ("games" / "active_members"), "avg_rating")
    WHERE "active_members" IS NOT NULL;
CREATE INDEX "ix_clan_search_avg_rating"
    ON "clan"("avg_rating", "id", ("games" / "active_members"), "active_members")
    WHERE "active_members" IS NOT NULL;
CREATE INDEX "ix_clan_search_games"
    ON "clan"(("games" / "active_members"), "id", "active_members", "avg_rating")
    WHERE "active_members" IS NOT NULL;
