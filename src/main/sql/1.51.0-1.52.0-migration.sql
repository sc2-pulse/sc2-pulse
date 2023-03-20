CREATE TABLE "player_character_link"
(
    "player_character_id" BIGINT NOT NULL,
    "type" SMALLINT NOT NULL,
    "url" TEXT NOT NULL,

    PRIMARY KEY("player_character_id", "type"),

    CONSTRAINT "fk_player_character_link_player_character_id"
        FOREIGN KEY ("player_character_id")
        REFERENCES "player_character"("id")
        ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE INDEX "ix_player_character_link_type_url" ON "player_character_link"("type", "url");
