-- Copyright (C) 2021 Oleksandr Masniuk and contributors
-- SPDX-License-Identifier: AGPL-3.0-or-later
---

CREATE TABLE "account_role"
(
    "account_id" BIGINT NOT NULL,
    "role" SMALLINT NOT NULL,

    PRIMARY KEY ("account_id", "role")
);


CREATE TABLE "player_character_report"
(
    "id" SERIAL,
    "player_character_id" BIGINT NOT NULL,
    "additional_player_character_id" BIGINT,
    "type" SMALLINT NOT NULL,
    "status" BOOLEAN,
    "status_change_timestamp" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    PRIMARY KEY("id"),

    CONSTRAINT "fk_player_character_report_player_character_id"
        FOREIGN KEY ("player_character_id")
        REFERENCES "player_character"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "fk_player_character_report_additional_player_character_id"
        FOREIGN KEY ("additional_player_character_id")
        REFERENCES "player_character"("id")
        ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE UNIQUE INDEX "uq_player_character_report_player_character_id_type_additional_player_character_id"
    ON "player_character_report"("player_character_id", "type", COALESCE("additional_player_character_id", -1));
CREATE INDEX "ix_player_character_report_additional_player_character_id"
    ON "player_character_report"("additional_player_character_id") WHERE "additional_player_character_id" IS NOT NULL;
CREATE INDEX "ix_player_character_report_status_status_change_timestamp"
    ON "player_character_report"("status", "status_change_timestamp");

CREATE TABLE "evidence"
(
    "id" SERIAL,
    "player_character_report_id" INTEGER NOT NULL,
    "reporter_account_id" BIGINT,
    "reporter_ip" bytea NOT NULL,
    "description" VARCHAR(400) NOT NULL,
    "status" BOOLEAN,
    "status_change_timestamp" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    "created" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    PRIMARY KEY("id"),

    CONSTRAINT "fk_evidence_player_character_report_id"
        FOREIGN KEY ("player_character_report_id")
        REFERENCES "player_character_report"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "fk_evidence_reporter_account_id"
        FOREIGN KEY ("reporter_account_id")
        REFERENCES "account"("id")
        ON DELETE SET NULL ON UPDATE CASCADE
);
CREATE INDEX "ix_evidence_created_reporter_ip" ON "evidence"("created", "reporter_ip");
CREATE INDEX "ix_evidence_created_reporter_account_id"
    ON "evidence"("created", "reporter_account_id")
    WHERE "reporter_account_id" IS NOT NULL;
CREATE INDEX "ix_evidence_player_character_report_id" ON "evidence"("player_character_report_id");
CREATE INDEX "ix_evidence_status_status_change_timestamp"
    ON "evidence"("status", "status_change_timestamp");

CREATE TABLE "evidence_vote"
(
    "evidence_id" INTEGER NOT NULL,
    "voter_account_id" BIGINT NOT NULL,
    "evidence_created" TIMESTAMP WITH TIME ZONE NOT NULL,
    "vote" BOOLEAN NOT NULL,
    "updated" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    PRIMARY KEY ("evidence_id", "voter_account_id"),

    CONSTRAINT "fk_evidence_vote_evidence_id"
        FOREIGN KEY ("evidence_id")
        REFERENCES "evidence"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "fk_evidence_vote_voter_account_id"
        FOREIGN KEY ("voter_account_id")
        REFERENCES "account"("id")
        ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE INDEX "ix_evidence_vote_updated" ON "evidence_vote"("updated");
