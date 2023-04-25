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
