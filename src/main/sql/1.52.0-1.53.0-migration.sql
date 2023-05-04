CREATE TABLE "clan_member_event"
(
    "player_character_id" BIGINT NOT NULL,
    "clan_id" INTEGER NOT NULL,
    "type" SMALLINT NOT NULL,
    "created" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    "seconds_since_previous" INTEGER,

    PRIMARY KEY("player_character_id", "created"),

    CONSTRAINT "fk_clan_member_event_player_character_id"
        FOREIGN KEY ("player_character_id")
        REFERENCES "player_character"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "fk_clan_member_event_clan_id"
        FOREIGN KEY ("clan_id")
        REFERENCES "clan"("id")
        ON DELETE CASCADE ON UPDATE CASCADE
);

INSERT INTO "clan_member_event"("player_character_id", "clan_id", "type")
SELECT "player_character_id", "clan_id", 1
FROM "clan_member";

CREATE INDEX "ix_clan_member_event_clan" ON "clan_member_event"("clan_id", "created", "player_character_id");

DROP TABLE "persistent_logins";
DROP TABLE "authentication_request";

CREATE TABLE "account_property"
(
    "account_id" BIGINT NOT NULL,
    "type" SMALLINT NOT NULL,
    "value" TEXT NOT NULL,

    PRIMARY KEY("account_id", "type"),

    CONSTRAINT "fk_account_property_account_id"
        FOREIGN KEY ("account_id")
        REFERENCES "account"("id")
        ON DELETE CASCADE ON UPDATE CASCADE
);

ALTER TABLE "league_stats"
    ADD COLUMN "terran_team_count" INTEGER,
    ADD COLUMN "protoss_team_count" INTEGER,
    ADD COLUMN "zerg_team_count" INTEGER,
    ADD COLUMN "random_team_count" INTEGER;
CREATE INDEX "ix_team_season_queue_type" ON "team"("season", "queue_type") WHERE "queue_type" = 201;

