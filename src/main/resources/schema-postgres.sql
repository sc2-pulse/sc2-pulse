-- Copyright (C) 2020 Oleksandr Masniuk and contributors
-- SPDX-License-Identifier: AGPL-3.0-or-later
---

CREATE OR REPLACE FUNCTION increase_own_version()
RETURNS trigger
AS
'
    BEGIN
        IF(OLD.version != NEW.version OR OLD.* IS NOT DISTINCT FROM NEW.*) THEN return NEW; END IF;
        NEW.version := OLD.version + 1;
        RETURN NEW;
    END
'
LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION increase_own_version_excluding_updated()
RETURNS trigger
AS
'
    DECLARE new_updated TIMESTAMP WITH TIME ZONE;
    BEGIN
        IF(OLD.version != NEW.version) THEN return NEW; END IF;
        new_updated := NEW.updated;
        NEW.updated := OLD.updated;
        IF(OLD.* IS NOT DISTINCT FROM NEW.*)
        THEN
            NEW.updated := new_updated;
            return NEW;
        END IF;

        NEW.version := OLD.version + 1;
        NEW.updated := new_updated;
        RETURN NEW;
    END
'
LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION increase_foreign_version()
RETURNS trigger
AS
'
    DECLARE CUR record;
    BEGIN
        IF(TG_OP = ''UPDATE'' AND OLD.* IS NOT DISTINCT FROM NEW.*) THEN return NEW; END IF;
        CUR := COALESCE(NEW, OLD);
        EXECUTE
            ''UPDATE '' || TG_ARGV[0] || ''
            SET version = version + 1
            WHERE id = $1.'' || TG_ARGV[0] || ''_id''
            USING CUR;
        RETURN CUR;
    END
'
LANGUAGE plpgsql;

CREATE TABLE "clan"
(
    "id" SERIAL,
    "tag" TEXT NOT NULL,
    "region" SMALLINT NOT NULL,
    "name" TEXT,
    "members" SMALLINT,
    "active_members" SMALLINT,
    "avg_rating" SMALLINT,
    "avg_league_type" SMALLINT,
    "games" INTEGER,

    PRIMARY KEY("id"),

    CONSTRAINT "uq_clan_tag_region"
        UNIQUE ("tag", "region")
);

CREATE INDEX "ix_clan_search_members"
    ON "clan"("members", "id", "active_members", "avg_rating", ("games"::double precision / "active_members" / 60), "region")
    WHERE "active_members" IS NOT NULL;
CREATE INDEX "ix_clan_search_active_members"
    ON "clan"("active_members", "id", ("games"::double precision / "active_members" / 60), "avg_rating", "region")
    WHERE "active_members" IS NOT NULL;
CREATE INDEX "ix_clan_search_avg_rating"
    ON "clan"("avg_rating", "id", ("games"::double precision / "active_members" / 60), "active_members", "region")
    WHERE "active_members" IS NOT NULL;
CREATE INDEX "ix_clan_search_games"
    ON "clan"(("games"::double precision / "active_members" / 60), "id", "active_members", "avg_rating", "region")
    WHERE "active_members" IS NOT NULL;
CREATE INDEX "ix_clan_name" ON "clan"(LOWER("name") text_pattern_ops) WHERE "name" IS NOT NULL;
CREATE INDEX "ix_clan_tag" ON "clan"(LOWER("tag") text_pattern_ops);

CREATE TABLE "account"
(

    "id" BIGSERIAL,
    "partition" SMALLINT NOT NULL,
    "battle_tag" TEXT NOT NULL,
    "updated" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    "battle_tag_last_season" SMALLINT NOT NULL DEFAULT 0,
    "hidden" BOOLEAN,
    "anonymous" BOOLEAN,

    PRIMARY KEY ("id"),

    CONSTRAINT "uq_account_partition_battle_tag"
        UNIQUE ("partition", "battle_tag")

);

CREATE INDEX "ix_account_battle_tag" ON "account"(LOWER("battle_tag") text_pattern_ops);
CREATE INDEX "ix_account_updated" ON "account"("updated");

CREATE TABLE "account_role"
(
    "account_id" BIGINT NOT NULL,
    "role" SMALLINT NOT NULL,

    PRIMARY KEY ("account_id", "role"),

    CONSTRAINT "fk_account_role_account_id"
        FOREIGN KEY ("account_id")
        REFERENCES "account"("id")
        ON DELETE CASCADE ON UPDATE CASCADE
);

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

CREATE TABLE "discord_user"
(
    "id" BIGINT NOT NULL,
    "name" TEXT NOT NULL,
    "discriminator" SMALLINT,

    PRIMARY KEY("id")
);
CREATE UNIQUE INDEX "uq_discord_user_name_discriminator" ON "discord_user"("name", "discriminator") WHERE "discriminator" IS NOT NULL;
CREATE UNIQUE INDEX "uq_discord_user_name" ON "discord_user"("name") WHERE "discriminator" IS NULL;

CREATE TABLE "account_discord_user"
(
    "account_id" BIGINT NOT NULL,
    "discord_user_id" BIGINT NOT NULL,
    "public" BOOLEAN,

    PRIMARY KEY("account_id"),

    CONSTRAINT "fk_account_discord_user_account_id"
        FOREIGN KEY ("account_id")
        REFERENCES "account"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "fk_account_discord_user_discord_user_id"
        FOREIGN KEY ("discord_user_id")
        REFERENCES "discord_user"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT "uq_account_discord_user_discord_user_id"
        UNIQUE ("discord_user_id")
);

CREATE TABLE "player_character"
(

    "id" BIGSERIAL,
    "account_id" BIGINT NOT NULL,
    "battlenet_id" BIGINT NOT NULL,
    "region" SMALLINT NOT NULL,
    "realm" SMALLINT NOT NULL,
    "name" TEXT NOT NULL,
    "updated" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    "anonymous" BOOLEAN,

    PRIMARY KEY ("id"),

    CONSTRAINT "fk_player_character_account_id"
        FOREIGN KEY ("account_id")
        REFERENCES "account"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT "uq_player_character_region_realm_battlenet_id"
        UNIQUE ("region", "realm", "battlenet_id")

);

CREATE INDEX "ix_player_character_account_id" ON "player_character"("account_id");
CREATE INDEX "ix_player_character_battlenet_id" ON "player_character"("battlenet_id");
CREATE INDEX "ix_player_character_name" ON "player_character"(LOWER("name") text_pattern_ops);
CREATE INDEX "ix_player_character_updated" ON "player_character"("updated");

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

CREATE TABLE "clan_member"
(
    "player_character_id" BIGINT NOT NULL,
    "clan_id" INTEGER NOT NULL,
    "updated" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    PRIMARY KEY("player_character_id"),

    CONSTRAINT "fk_clan_member_player_character_id"
        FOREIGN KEY ("player_character_id")
        REFERENCES "player_character"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "fk_clan_member_clan_id"
        FOREIGN KEY ("clan_id")
        REFERENCES "clan"("id")
        ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE INDEX "ix_clan_member_clan_id" ON "clan_member"("clan_id");
CREATE INDEX "ix_clan_member_updated" ON "clan_member"("updated");

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

CREATE INDEX "ix_clan_member_event_clan" ON "clan_member_event"("clan_id", "created", "player_character_id");

CREATE TABLE "season"
(

    "id" SERIAL,
    "region" SMALLINT NOT NULL,
    "battlenet_id" SMALLINT NOT NULL,
    "year" SMALLINT NOT NULL,
    "number" SMALLINT NOT NULL,
    "start" DATE NOT NULL,
    "end" DATE NOT NULL,

    PRIMARY KEY ("id"),

    CONSTRAINT "uq_season_region_battlenet_id"
        UNIQUE ("region", "battlenet_id")
);

CREATE TABLE "league"
(

    "id" SERIAL,
    "season_id" INTEGER NOT NULL,
    "type" SMALLINT NOT NULL,
    "queue_type" SMALLINT NOT NULL,
    "team_type" SMALLINT NOT NULL,

    PRIMARY KEY ("id"),

    CONSTRAINT "fk_league_season_id"
        FOREIGN KEY ("season_id")
        REFERENCES "season"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT "uq_league_season_id_type_queue_type_team_type"
        UNIQUE ("season_id", "type", "queue_type", "team_type")

);

CREATE TABLE "population_state"
(
    "id" SERIAL,
    "league_id" INTEGER NOT NULL,
    "global_team_count" INTEGER NOT NULL,
    "region_team_count" INTEGER NOT NULL,
    "league_team_count" INTEGER,

    PRIMARY KEY("id"),

    CONSTRAINT "fk_population_state_league_id"
        FOREIGN KEY ("league_id")
        REFERENCES "league"("id")
        ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE "league_tier"
(

    "id" SERIAL,
    "league_id" INTEGER NOT NULL,
    "type" SMALLINT NOT NULL,
    "min_rating" SMALLINT,
    "max_rating" SMALLINT,

    PRIMARY KEY ("id"),

    CONSTRAINT "fk_league_tier_league_id"
        FOREIGN KEY ("league_id")
        REFERENCES "league"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT "uq_league_tier_league_id_type"
        UNIQUE ("league_id", "type")

);

CREATE TABLE "division"
(

    "id" SERIAL,
    "league_tier_id" INTEGER NOT NULL,
    "battlenet_id" BIGINT NOT NULL,

    PRIMARY KEY ("id"),

    CONSTRAINT "fk_division_league_tier_id"
        FOREIGN KEY ("league_tier_id")
        REFERENCES "league_tier"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT "uq_division_league_tier_id_battlenet_id"
        UNIQUE("league_tier_id", "battlenet_id")

);

CREATE TABLE "team"
(

    "id" BIGSERIAL,
    "legacy_id" NUMERIC NOT NULL,
    "division_id" INTEGER NOT NULL,
    "population_state_id" INTEGER,
    "season" SMALLINT NOT NULL,
    "region" SMALLINT NOT NULL,
    "league_type" SMALLINT NOT NULL,
    "queue_type" SMALLINT NOT NULL,
    "team_type" SMALLINT NOT NULL,
    "tier_type" SMALLINT,
    "rating" SMALLINT NOT NULL,
    "points" SMALLINT NOT NULL,
    "wins" SMALLINT NOT NULL,
    "losses" SMALLINT NOT NULL,
    "ties" SMALLINT NOT NULL,
    "global_rank" INTEGER,
    "region_rank" INTEGER,
    "league_rank" INTEGER,
    "last_played" TIMESTAMP WITH TIME ZONE,

    PRIMARY KEY ("id"),

    CONSTRAINT "fk_team_division_id"
        FOREIGN KEY ("division_id")
        REFERENCES "division"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "fk_team_population_state_id"
        FOREIGN KEY ("population_state_id")
        REFERENCES "population_state"("id")
        ON DELETE SET NULL ON UPDATE CASCADE,

    CONSTRAINT "uq_team_queue_type_region_legacy_id_season"
        UNIQUE ("queue_type", "region", "legacy_id", "season")

)
WITH (fillfactor = 50);

CREATE INDEX "ix_team_ladder_search_full" ON "team"("season", "queue_type", "team_type", "rating", "id");
CREATE INDEX "ix_team_season_queue_type" ON "team"("season", "queue_type") WHERE "queue_type" = 201;

CREATE TABLE "team_member"
(
    "team_id" BIGINT NOT NULL,
    "team_season" SMALLINT NOT NULL,
    "team_queue_type" SMALLINT NOT NULL,
    "player_character_id" BIGINT NOT NULL,
    "terran_games_played" SMALLINT,
    "protoss_games_played" SMALLINT,
    "zerg_games_played" SMALLINT,
    "random_games_played" SMALLINT,

    PRIMARY KEY ("team_id", "player_character_id"),

    CONSTRAINT "fk_team_member_team_id"
        FOREIGN KEY ("team_id")
        REFERENCES "team"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "fk_team_player_character_id"
        FOREIGN KEY ("player_character_id")
        REFERENCES "player_character"("id")
        ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE INDEX "ix_fk_team_member_player_character_id" ON "team_member"("player_character_id");
CREATE INDEX "ix_team_member_group_search" ON "team_member"("player_character_id", "team_season", "team_queue_type");

CREATE TABLE "team_state"
(
    "team_id" BIGINT NOT NULL,
    "timestamp" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    "division_id" INTEGER NOT NULL,
    "population_state_id" INTEGER,
    "wins" SMALLINT,
    "games" SMALLINT NOT NULL,
    "rating" SMALLINT NOT NULL,
    "global_rank" INTEGER,
    "region_rank" INTEGER,
    "league_rank" INTEGER,
    "archived" BOOLEAN,
    "secondary" BOOLEAN,

    PRIMARY KEY ("team_id", "timestamp"),

    CONSTRAINT "fk_team_state_team_id"
        FOREIGN KEY ("team_id")
        REFERENCES "team"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "fk_team_state_division_id"
        FOREIGN KEY ("division_id")
        REFERENCES "division"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "fk_team_state_population_state_id"
        FOREIGN KEY ("population_state_id")
        REFERENCES "population_state"("id")
        ON DELETE SET NULL ON UPDATE CASCADE
);

CREATE INDEX "ix_team_state_timestamp" ON "team_state"("timestamp");
CREATE INDEX "ix_team_state_team_id_archived" ON "team_state"("team_id", "archived") WHERE "archived" = true;
CREATE INDEX "ix_team_state_secondary_timestamp" ON "team_state"("secondary", "timestamp") WHERE "secondary" = true;

CREATE TABLE "queue_stats"
(
    "id" BIGSERIAL,
    "season" SMALLINT NOT NULL,
    "queue_type" SMALLINT NOT NULL,
    "team_type" SMALLINT NOT NULL,
    "player_base" BIGINT NOT NULL,
    "player_count" INTEGER NOT NULL,
    "low_activity_player_count" INTEGER NOT NULL DEFAULT 0,
    "medium_activity_player_count" INTEGER NOT NULL DEFAULT 0,
    "high_activity_player_count" INTEGER NOT NULL DEFAULT 0,

    PRIMARY KEY ("id"),

    CONSTRAINT "uq_queue_stats_queue_type_team_type_season"
        UNIQUE ("queue_type", "team_type", "season")
);

CREATE TABLE "league_stats"
(
    "league_id" INTEGER NOT NULL,
    "team_count" INTEGER NOT NULL,
    "terran_games_played" INTEGER NOT NULL,
    "protoss_games_played" INTEGER NOT NULL,
    "zerg_games_played" INTEGER NOT NULL,
    "random_games_played" INTEGER NOT NULL,
    "terran_team_count" INTEGER,
    "protoss_team_count" INTEGER,
    "zerg_team_count" INTEGER,
    "random_team_count" INTEGER,

    PRIMARY KEY ("league_id"),

    CONSTRAINT "fk_league_stats_league_id"
        FOREIGN KEY ("league_id")
        REFERENCES "league"("id")
        ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE "season_state"
(
    "season_id" INTEGER NOT NULL,
    "period_start" TIMESTAMP WITH TIME ZONE NOT NULL,
    "player_count" SMALLINT NOT NULL,
    "total_games_played" INTEGER NOT NULL,
    "games_played" SMALLINT,

    PRIMARY KEY("period_start", "season_id"),

    CONSTRAINT "fk_season_state_season_id"
        FOREIGN KEY ("season_id")
        REFERENCES "season"("id")
        ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE "player_character_stats"
(
    "id" BIGSERIAL,
    "player_character_id" BIGINT NOT NULL,
    "queue_type" SMALLINT NOT NULL,
    "team_type" SMALLINT NOT NULL,
    "race" SMALLINT,
    "rating_max" SMALLINT NOT NULL,
    "league_max" SMALLINT NOT NULL,
    "games_played" INTEGER NOT NULL,

    PRIMARY KEY ("id"),

    CONSTRAINT "fk_player_character_stats_player_character_id"
        FOREIGN KEY ("player_character_id")
        REFERENCES "player_character"("id")
        ON DELETE CASCADE ON UPDATE CASCADE
)
WITH (fillfactor = 90);

CREATE UNIQUE INDEX "uq_player_character_stats_main"
    ON "player_character_stats"("player_character_id", COALESCE("race", -32768), "queue_type", "team_type");

CREATE TABLE "account_following"
(
    "account_id" BIGINT NOT NULL,
    "following_account_id" BIGINT NOT NULL,

    PRIMARY KEY ("account_id", "following_account_id"),

    CONSTRAINT "fk_account_following_account_id"
        FOREIGN KEY ("account_id")
        REFERENCES "account"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "fk_account_following_following_account_id"
        FOREIGN KEY ("following_account_id")
        REFERENCES "account"("id")
        ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE "twitch_user"
(
    "id" BIGINT NOT NULL,
    "login" TEXT NOT NULL,
    "sub_only_vod" BOOLEAN NOT NULL DEFAULT false,

    PRIMARY KEY ("id")
);

CREATE TABLE "twitch_video"
(
    "id" BIGINT NOT NULL,
    "twitch_user_id" BIGINT NOT NULL,
    "url" TEXT NOT NULL,
    "begin" TIMESTAMP WITH TIME ZONE NOT NULL,
    "end" TIMESTAMP WITH TIME ZONE NOT NULL,

    PRIMARY KEY ("id"),

    CONSTRAINT "fk_twitch_video_twitch_user_id"
        FOREIGN KEY ("twitch_user_id")
        REFERENCES "twitch_user"("id")
        ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE INDEX "ix_twitch_user_login" ON "twitch_user"(LOWER("login"));
CREATE INDEX "ix_twitch_video_twitch_user_id_begin_end" ON "twitch_video"("twitch_user_id", "begin", "end");

CREATE TABLE "pro_player"
(
    "id" BIGSERIAL,
    "aligulac_id" BIGINT,
    "nickname" TEXT NOT NULL,
    "name" TEXT,
    "country" CHAR(2),
    "team" TEXT,
    "birthday" DATE,
    "earnings" INTEGER,
    "updated" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    "version" SMALLINT NOT NULL DEFAULT 1,

    PRIMARY KEY ("id"),

    CONSTRAINT "uq_pro_player_aligulac_id"
        UNIQUE("aligulac_id")
);

CREATE INDEX "ix_pro_player_updated" ON "pro_player"("updated");
CREATE INDEX "ix_pro_player_nickname" ON "pro_player"(LOWER("nickname"));

CREATE TRIGGER update_version
BEFORE UPDATE
ON pro_player
FOR EACH ROW
EXECUTE FUNCTION increase_own_version_excluding_updated();

CREATE TABLE "social_media_link"
(
    "pro_player_id" BIGINT NOT NULL,
    "type" SMALLINT NOT NULL,
    "url" TEXT NOT NULL,
    "updated" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    "service_user_id" TEXT,
    "protected" BOOLEAN,

    PRIMARY KEY("pro_player_id", "type"),

    CONSTRAINT "fk_social_media_link_pro_player_id"
        FOREIGN KEY ("pro_player_id")
        REFERENCES "pro_player"("id")
        ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE INDEX "ix_social_media_link_updated" ON "social_media_link"("updated");
CREATE INDEX "ix_social_media_link_type_service_user_id"
ON "social_media_link"("type", "service_user_id")
WHERE "service_user_id" IS NOT NULL;

CREATE TRIGGER update_parent_version
AFTER INSERT OR DELETE OR UPDATE
ON social_media_link
FOR EACH ROW
EXECUTE FUNCTION increase_foreign_version('pro_player');

CREATE TABLE "pro_player_account"
(
    "pro_player_id" BIGINT NOT NULL,
    "account_id" BIGINT NOT NULL,
    "revealer_account_id" BIGINT,
    "updated" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    "protected" BOOLEAN,

    PRIMARY KEY("account_id"),

    CONSTRAINT "fk_pro_player_account_pro_player_id"
        FOREIGN KEY ("pro_player_id")
        REFERENCES "pro_player"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "fk_pro_player_account_account_id"
        FOREIGN KEY ("account_id")
        REFERENCES "account"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "fk_pro_player_account_revealer_account_id"
        FOREIGN KEY ("revealer_account_id")
        REFERENCES "account"("id")
        ON DELETE SET NULL ON UPDATE CASCADE

);

CREATE INDEX "ix_pro_player_account_pro_player_id" ON "pro_player_account"("pro_player_id");
CREATE INDEX "ix_pro_player_account_updated" ON "pro_player_account"("updated");

CREATE TABLE "pro_team"
(
    "id" BIGSERIAL,
    "aligulac_id" BIGINT,
    "name" TEXT NOT NULL,
    "short_name" TEXT,
    "updated" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    PRIMARY KEY("id")
);

CREATE UNIQUE INDEX "uq_pro_team_name" ON "pro_team"(LOWER(REPLACE("name", ' ', '')));
CREATE INDEX "ix_pro_team_updated" ON "pro_team"("updated");

CREATE TABLE "pro_team_member"
(
    "pro_team_id" BIGINT NOT NULL,
    "pro_player_id" BIGINT NOT NULL,
    "updated" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

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

CREATE INDEX "ix_pro_team_member_pro_team_id" ON "pro_team_member"("pro_team_id");
CREATE INDEX "ix_pro_team_member_updated" ON "pro_team_member"("updated");

CREATE OR REPLACE FUNCTION update_team_member_meta_data()
RETURNS trigger
AS
'
    BEGIN
        SELECT team.season, team.queue_type
        INTO NEW.team_season, NEW.team_queue_type
        FROM team
        WHERE team.id = NEW.team_id;
        return NEW;
    END
'
LANGUAGE plpgsql;
CREATE TRIGGER update_meta_data
BEFORE INSERT
ON team_member
FOR EACH ROW
EXECUTE FUNCTION update_team_member_meta_data();

CREATE TABLE "map"
(
    "id" SERIAL,
    "name" TEXT NOT NULL,

    PRIMARY KEY ("id"),

    CONSTRAINT "uq_map_name"
        UNIQUE("name")
);

CREATE TABLE "map_stats"
(
    "id" SERIAL,
    "map_id" INTEGER,
    "league_id" INTEGER NOT NULL,
    "race" SMALLINT NOT NULL,
    "versus_race" SMALLINT NOT NULL,
    "games" INTEGER NOT NULL,
    "games_with_duration" INTEGER NOT NULL,
    "wins" INTEGER NOT NULL,
    "losses" INTEGER NOT NULL,
    "ties" INTEGER NOT NULL,
    "duration" INTEGER NOT NULL,

    PRIMARY KEY("id"),

    CONSTRAINT "fk_map_stats_map_id"
        FOREIGN KEY ("map_id")
        REFERENCES "map"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "fk_map_stats_league_id"
        FOREIGN KEY ("league_id")
        REFERENCES "league"("id")
        ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE UNIQUE INDEX "uq_map_stats_league_id_map_id_race_versus_race"
    ON "map_stats"("league_id", COALESCE("map_id", -1), "race", "versus_race");

CREATE TABLE "match"
(
    "id" BIGSERIAL,
    "date" TIMESTAMP WITH TIME ZONE NOT NULL,
    "type" SMALLINT NOT NULL,
    "map_id" INTEGER NOT NULL,
    "region" SMALLINT NOT NULL,
    "updated" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    "duration" SMALLINT,
    "vod" BOOLEAN,
    "sub_only_vod" BOOLEAN,
    "rating_min" INTEGER,
    "rating_max" INTEGER,
    "race" TEXT,
    "race_vod" TEXT,

    PRIMARY KEY ("id"),

    CONSTRAINT "fk_match_map_id"
        FOREIGN KEY ("map_id")
        REFERENCES "map"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT "uq_match_date_type_map_id_region"
        UNIQUE("date", "type", "map_id", "region")
)
WITH (fillfactor = 90);

CREATE INDEX "ix_match_updated" ON "match"("updated");
CREATE INDEX "ix_match_vod_search" ON "match"("date", "type", "map_id", "vod", "sub_only_vod", "race_vod" text_pattern_ops, "race" text_pattern_ops, "rating_min", "duration", "rating_max")
    WHERE "vod" = true;

CREATE TABLE "match_participant"
(
    "match_id" BIGINT NOT NULL,
    "player_character_id" BIGINT NOT NULL,
    "team_id" BIGINT,
    "twitch_video_id" BIGINT,
    "twitch_video_offset" INTEGER,
    "team_state_timestamp" TIMESTAMP WITH TIME ZONE,
    "decision" SMALLINT NOT NULL,
    "rating_change" SMALLINT,

    PRIMARY KEY ("match_id", "player_character_id"),

    CONSTRAINT "fk_match_participant_match_id"
        FOREIGN KEY ("match_id")
        REFERENCES "match"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "fk_match_participant_player_character_id"
        FOREIGN KEY ("player_character_id")
        REFERENCES "player_character"("id")
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "fk_match_participant_team_id"
        FOREIGN KEY ("team_id")
        REFERENCES "team"("id")
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT "fk_match_participant_team_state_uid"
        FOREIGN KEY ("team_id", "team_state_timestamp")
        REFERENCES "team_state"("team_id", "timestamp")
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT "fk_match_participant_twitch_video_id"
        FOREIGN KEY ("twitch_video_id")
        REFERENCES "twitch_video"("id")
        ON DELETE SET NULL ON UPDATE CASCADE
);

CREATE INDEX "ix_match_participant_player_character_id" ON "match_participant"("player_character_id");
CREATE INDEX "ix_match_participant_team_id_team_state_timestamp" ON "match_participant"("team_id", "team_state_timestamp")
    WHERE "team_id" IS NOT NULL
    OR "team_state_timestamp" IS NOT NULL;
CREATE INDEX "ix_match_participant_twitch_video_id"
    ON "match_participant"("twitch_video_id")
    WHERE "twitch_video_id" IS NOT NULL;

CREATE TABLE "var"
(
    "key" TEXT NOT NULL,
    "value" TEXT,

    PRIMARY KEY ("key")
);

CREATE TABLE "player_character_report"
(
    "id" SERIAL,
    "player_character_id" BIGINT NOT NULL,
    "additional_player_character_id" BIGINT,
    "type" SMALLINT NOT NULL,
    "status" BOOLEAN,
    "restrictions" BOOLEAN NOT NULL DEFAULT false,
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
    "reporter_ip" bytea,
    "description" TEXT NOT NULL,
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

CREATE TABLE oauth2_authorized_client
(
    client_registration_id VARCHAR(100) NOT NULL,
    principal_name VARCHAR(200) NOT NULL,
    access_token_type VARCHAR(100) NOT NULL,
    access_token_value bytea NOT NULL,
    access_token_issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    access_token_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    access_token_scopes VARCHAR(1000),
    refresh_token_value bytea,
    refresh_token_issued_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    PRIMARY KEY (client_registration_id, principal_name)
);

CREATE TABLE "notification"
(
    "id" BIGSERIAL,
    "account_id" BIGINT NOT NULL,
    "message" TEXT NOT NULL,
    "created" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    PRIMARY KEY ("id"),

    CONSTRAINT "fk_notification_account_id"
        FOREIGN KEY ("account_id")
        REFERENCES "account"("id")
        ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TYPE player_character_summary AS
(
    player_character_id BIGINT,
    race SMALLINT,
    games INTEGER,
    rating_avg SMALLINT,
    rating_max SMALLINT,
    rating_last SMALLINT,
    league_type_last SMALLINT,
    global_rank_last INTEGER
);

CREATE OR REPLACE FUNCTION get_player_character_summary
(character_ids BIGINT[], from_timestamp TIMESTAMP WITH TIME ZONE, races SMALLINT[])
RETURNS SETOF player_character_summary
AS
'
DECLARE
    table_record RECORD;
    prev_table_record RECORD;
    result player_character_summary[];
    cur_season INTEGER;
    cur_player_character_id BIGINT;
    cur_mmr SMALLINT[];
    cur_games INTEGER DEFAULT -1;
    prev_games INTEGER DEFAULT 0;
    cur_legacy_id NUMERIC;
    cur_legacy_id_text TEXT;
BEGIN
FOR table_record IN
WITH team_filter AS
(
    SELECT
    team.season,
    team.league_type,
    team.global_rank,
    team.legacy_id,
    team_member.player_character_id,
    team.wins + team.losses + team.ties AS games,
    team.rating,
    (season.end + INTERVAL ''7 days'')::timestamp AS "timestamp"
    FROM team
    INNER JOIN team_member ON team.id = team_member.team_id
    INNER JOIN season ON team.region = season.region AND team.season = season.battlenet_id
    WHERE player_character_id = ANY(character_ids)
    AND team.queue_type = 201
    AND substring(team.legacy_id::text, char_length(team.legacy_id::text))::smallint = ANY(races)
    AND season.end >= from_timestamp
),
team_state_filter AS
(
    SELECT
    team.season,
    null::INTEGER AS league_type,
    null::INTEGER AS global_rank,
    team.legacy_id,
    team_member.player_character_id,
    team_state.games,
    team_state.rating,
    team_state.timestamp
    FROM team_state
    INNER JOIN team ON team_state.team_id = team.id
    INNER JOIN team_member ON team.id = team_member.team_id
    WHERE player_character_id = ANY(character_ids)
    AND team.queue_type = 201
    AND substring(team.legacy_id::text, char_length(team.legacy_id::text))::smallint = ANY(races)
    AND team_state.timestamp >= from_timestamp
)
SELECT *
FROM
(
    SELECT * FROM team_filter
    UNION ALL
    SELECT * FROM team_state_filter
) t
ORDER BY player_character_id, legacy_id, season, timestamp
LOOP

    IF cur_legacy_id IS NULL THEN
        cur_legacy_id = table_record.legacy_id;
        cur_player_character_id = table_record.player_character_id;
        cur_season = table_record.season;
    END IF;

    IF table_record.legacy_id <> cur_legacy_id THEN
        cur_legacy_id_text = cur_legacy_id::text;
        result = array_append(result, row
        (
            cur_player_character_id,
            substring(cur_legacy_id_text, char_length(cur_legacy_id_text))::smallint,
            cur_games,
            (SELECT AVG(x) FROM unnest(cur_mmr) x),
            (SELECT MAX(x) FROM unnest(cur_mmr) x),
            cur_mmr[array_upper(cur_mmr, 1)],
            prev_table_record.league_type,
            prev_table_record.global_rank
        )::player_character_summary);
        cur_games = -1;
        cur_mmr = array[]::SMALLINT[];
        cur_legacy_id = table_record.legacy_id;
        cur_player_character_id = table_record.player_character_id;
    END IF;

    cur_games =
    CASE
        WHEN cur_games = -1 THEN 1
        WHEN table_record.season <> cur_season THEN cur_games + table_record.games
        WHEN table_record.games < prev_games THEN cur_games + table_record.games
        ELSE cur_games + (table_record.games - prev_games)
    END;

    IF table_record.season <> cur_season THEN cur_season = table_record.season; END IF;
    prev_games = table_record.games;
    cur_mmr = array_append(cur_mmr, table_record.rating);
    prev_table_record = table_record;
END LOOP;

IF cur_games <> -1 THEN
    cur_legacy_id_text = cur_legacy_id::text;
    result = array_append(result, row
    (
        cur_player_character_id,
        substring(cur_legacy_id_text, char_length(cur_legacy_id_text))::smallint,
        cur_games,
        (SELECT AVG(x) FROM unnest(cur_mmr) x),
        (SELECT MAX(x) FROM unnest(cur_mmr) x),
        cur_mmr[array_upper(cur_mmr, 1)],
        table_record.league_type,
        table_record.global_rank
    )::player_character_summary);
END IF;

RETURN QUERY SELECT * FROM unnest(result);
END
'
LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION get_top_percentage_league_lotv(rank INTEGER, teamCount DOUBLE PRECISION, gm BOOLEAN)
RETURNS SMALLINT
AS
'
DECLARE percentage DOUBLE PRECISION;
BEGIN
percentage = (rank / teamCount) * 100;
RETURN
CASE
    WHEN gm = true AND rank <= 200 THEN 6
    WHEN percentage <= 4 THEN 5
    WHEN percentage <= 27 THEN 4
    WHEN percentage <= 50 THEN 3
    WHEN percentage <= 73 THEN 2
    WHEN percentage <= 96 THEN 1
    ELSE 0
END;
END
'
LANGUAGE plpgsql;

-- This function exists because races are not normalized properly. Remove it when it is no longer the case.
CREATE OR REPLACE FUNCTION get_favorite_race
(terranGames SMALLINT, protossGames SMALLINT, zergGames SMALLINT, randomGames SMALLINT)
RETURNS SMALLINT
AS
'
    DECLARE
        maxGames SMALLINT;
    BEGIN
        SELECT MAX(x)::smallint INTO maxGames FROM unnest(ARRAY[terranGames, protossGames, zergGames, randomGames]) x;
        return CASE
            WHEN terranGames = maxGames THEN 1
            WHEN protossGames = maxGames THEN 2
            WHEN zergGames = maxGames THEN 3
            ELSE 4
        END;
    END
'
LANGUAGE plpgsql;
