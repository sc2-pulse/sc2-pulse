-- Copyright (C) 2022 Oleksandr Masniuk
-- SPDX-License-Identifier: AGPL-3.0-or-later
---

ALTER TABLE "player_character"
    ADD COLUMN "updated" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW();
CREATE INDEX "ix_player_character_updated" ON "player_character"("updated");

ALTER TABLE "account"
    ADD COLUMN "updated" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW();
CREATE INDEX "ix_account_updated" ON "account"("updated");

ALTER TABLE "evidence"
    ALTER COLUMN "reporter_ip" DROP NOT NULL;
UPDATE "evidence" SET "reporter_ip" = null;

