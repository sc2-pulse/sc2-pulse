-- Copyright (C) 2020 Oleksandr Masniuk and contributors
-- SPDX-License-Identifier: AGPL-3.0-or-later
---

CREATE TABLE "pro_player"
(
    "id" BIGSERIAL,
    "revealed_id" bytea NOT NULL,
    "aligulac_id" BIGINT,
    "nickname" VARCHAR(50) NOT NULL,
    "name" VARCHAR(50) NOT NULL,
    "country" CHAR(2),
    "team" VARCHAR(50),
    "birthday" DATE,
    "earnings" INTEGER,
    "updated" TIMESTAMP NOT NULL DEFAULT NOW(),

    PRIMARY KEY ("id"),

    CONSTRAINT "uq_pro_player_revealed_id"
        UNIQUE("revealed_id")
);

CREATE INDEX "ix_pro_player_updated" ON "pro_player"("updated");
CREATE INDEX "ix_pro_player_nickname" ON "pro_player"(LOWER("nickname"));

CREATE TABLE "social_media_link"
(
    "pro_player_id" BIGINT NOT NULL,
    "type" SMALLINT NOT NULL,
    "url" VARCHAR(255) NOT NULL,
    "updated" TIMESTAMP NOT NULL DEFAULT NOW(),

    PRIMARY KEY("pro_player_id", "type"),

    CONSTRAINT "fk_social_media_link_pro_player_id"
        FOREIGN KEY ("pro_player_id")
        REFERENCES "pro_player"("id")
        ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE INDEX "ix_social_media_link_updated" ON "social_media_link"("updated");

CREATE TABLE "pro_player_account"
(
    "pro_player_id" BIGINT NOT NULL,
    "account_id" BIGINT NOT NULL,
    "updated" TIMESTAMP NOT NULL DEFAULT NOW(),

    PRIMARY KEY("account_id"),

    CONSTRAINT "fk_pro_player_account_pro_player_id"
        FOREIGN KEY ("pro_player_id")
        REFERENCES "pro_player"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "fk_pro_player_account_account_id"
        FOREIGN KEY ("account_id")
        REFERENCES "account"("id")
        ON DELETE CASCADE ON UPDATE CASCADE

);

CREATE INDEX "ix_pro_player_account_updated" ON "pro_player_account"("updated");

CREATE TABLE "pro_team"
(
    "id" BIGSERIAL,
    "aligulac_id" BIGINT,
    "name" VARCHAR(50) NOT NULL,
    "short_name" VARCHAR(50),
    "updated" TIMESTAMP NOT NULL DEFAULT NOW(),

    PRIMARY KEY("id")
);

CREATE UNIQUE INDEX "uq_pro_team_name" ON "pro_team"(LOWER(REPLACE("name", ' ', '')));
CREATE INDEX "ix_pro_team_updated" ON "pro_team"("updated");

CREATE TABLE "pro_team_member"
(
    "pro_team_id" BIGINT NOT NULL,
    "pro_player_id" BIGINT NOT NULL,
    "updated" TIMESTAMP NOT NULL DEFAULT NOW(),

    PRIMARY KEY ("pro_player_id"),

    CONSTRAINT "fk_pro_team_member_pro_team_id"
        FOREIGN KEY ("pro_team_id")
        REFERENCES "pro_team"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "fk_pro_team_member_pro_player_id"
        FOREIGN KEY ("pro_player_id")
        REFERENCES "pro_player"("id")
        ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE INDEX "ix_pro_team_member_updated" ON "pro_team_member"("updated");

DELETE FROM account WHERE battle_tag = '';

WITH team_filtered AS
(
    SELECT DISTINCT(team.id)
    FROM team_member INNER JOIN team ON team_member.team_id = team.id
    GROUP BY team.id HAVING COUNT(*) = 0
)
DELETE FROM team USING team_filtered
WHERE team.id = team_filtered.id;

WITH team_filtered AS
(
    SELECT DISTINCT(team.id)
    FROM team_member INNER JOIN team ON team_member.team_id = team.id
    WHERE team.season IS NOT NULL AND team.queue_type = 202 AND team.team_type = 0
    GROUP BY team.id HAVING COUNT(*) != 2
)
DELETE FROM team USING team_filtered
WHERE team.id = team_filtered.id;

WITH team_filtered AS
(
    SELECT DISTINCT(team.id)
    FROM team_member INNER JOIN team ON team_member.team_id = team.id
    WHERE team.season IS NOT NULL AND team.queue_type = 203 AND team.team_type = 0
    GROUP BY team.id HAVING COUNT(*) != 3
)
DELETE FROM team USING team_filtered
WHERE team.id = team_filtered.id;

WITH team_filtered AS
(
    SELECT DISTINCT(team.id)
    FROM team_member INNER JOIN team ON team_member.team_id = team.id
    WHERE team.season IS NOT NULL AND team.queue_type = 204 AND team.team_type = 0
    GROUP BY team.id HAVING COUNT(*) != 4
)
DELETE FROM team USING team_filtered
WHERE team.id = team_filtered.id;

WITH team_filtered AS
(
    SELECT DISTINCT(team.id)
    FROM team_member INNER JOIN team ON team_member.team_id = team.id
    WHERE team.season IS NOT NULL AND team.queue_type = 206 AND team.team_type = 0
    GROUP BY team.id HAVING COUNT(*) != 2
)
DELETE FROM team USING team_filtered
WHERE team.id = team_filtered.id;