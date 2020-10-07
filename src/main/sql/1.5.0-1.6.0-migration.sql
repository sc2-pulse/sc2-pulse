-- Copyright (C) 2020 Oleksandr Masniuk and contributors
-- SPDX-License-Identifier: AGPL-3.0-or-later
---

ALTER TABLE "season"
    ADD COLUMN "start" DATE NOT NULL DEFAULT NOW(),
    ADD COLUMN "end" DATE NOT NULL DEFAULT NOW();

UPDATE "season" SET "start" = '2016-10-18', "end" = '2016-11-22' WHERE "battlenet_id" = 29;
UPDATE "season" SET "start" = '2016-11-22', "end" = '2017-01-24' WHERE "battlenet_id" = 30;
UPDATE "season" SET "start" = '2017-01-24', "end" = '2017-05-02' WHERE "battlenet_id" = 31;
UPDATE "season" SET "start" = '2017-05-02', "end" = '2017-07-19' WHERE "battlenet_id" = 32;
UPDATE "season" SET "start" = '2017-07-19', "end" = '2017-10-19' WHERE "battlenet_id" = 33;
UPDATE "season" SET "start" = '2017-10-19', "end" = '2018-01-23' WHERE "battlenet_id" = 34;
UPDATE "season" SET "start" = '2018-01-23', "end" = '2018-05-15' WHERE "battlenet_id" = 35;
UPDATE "season" SET "start" = '2018-05-15', "end" = '2018-08-14' WHERE "battlenet_id" = 36;
UPDATE "season" SET "start" = '2018-08-14', "end" = '2018-11-20' WHERE "battlenet_id" = 37;
UPDATE "season" SET "start" = '2018-11-20', "end" = '2019-01-22' WHERE "battlenet_id" = 38;
UPDATE "season" SET "start" = '2019-01-22', "end" = '2019-05-22' WHERE "battlenet_id" = 39;
UPDATE "season" SET "start" = '2019-05-22', "end" = '2019-08-22' WHERE "battlenet_id" = 40;
UPDATE "season" SET "start" = '2019-08-22', "end" = '2019-11-25' WHERE "battlenet_id" = 41;
UPDATE "season" SET "start" = '2019-11-25', "end" = '2020-03-17' WHERE "battlenet_id" = 42;
UPDATE "season" SET "start" = '2020-03-17', "end" = '2020-06-10' WHERE "battlenet_id" = 43;
UPDATE "season" SET "start" = '2020-06-10', "end" = '2020-10-01' WHERE "battlenet_id" = 44;

ALTER TABLE "season"
    ALTER COLUMN "start" DROP DEFAULT,
    ALTER COLUMN "end" DROP DEFAULT;

ALTER TABLE "queue_stats"
    ADD COLUMN "low_activity_player_count" INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN "medium_activity_player_count" INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN "high_activity_player_count" INTEGER NOT NULL DEFAULT 0;