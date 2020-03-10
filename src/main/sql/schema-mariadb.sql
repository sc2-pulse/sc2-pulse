---
-- =========================LICENSE_START=========================
-- SC2 Ladder Generator
-- %%
-- Copyright (C) 2020 Oleksandr Masniuk
-- %%
-- This program is free software: you can redistribute it and/or modify
-- it under the terms of the GNU Affero General Public License as published by
-- the Free Software Foundation, either version 3 of the License, or
-- (at your option) any later version.
-- 
-- This program is distributed in the hope that it will be useful,
-- but WITHOUT ANY WARRANTY; without even the implied warranty of
-- MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
-- GNU General Public License for more details.
-- 
-- You should have received a copy of the GNU Affero General Public License
-- along with this program.  If not, see <http://www.gnu.org/licenses/>.
-- =========================LICENSE_END=========================
---
CREATE TABLE `account`
(

    `id` SERIAL,
    `region` TINYINT UNSIGNED NOT NULL,
    `battlenet_id` BIGINT UNSIGNED NOT NULL,
    `battle_tag` VARCHAR(30) NOT NULL,
    `updated` TIMESTAMP NOT NULL,

    PRIMARY KEY (`id`),

    CONSTRAINT `uq_account_region_battlenet_id`
        UNIQUE (`region`, `battlenet_id`),

    INDEX `ix_account_battle_tag` (`battle_tag`),
    INDEX `ix_account_updated` (`updated`)

);

CREATE TABLE `player_character`
(

    `id` SERIAL,
    `account_id` BIGINT UNSIGNED NOT NULL,
    `battlenet_id` BIGINT UNSIGNED NOT NULL,
    `realm` TINYINT UNSIGNED NOT NULL,
    `name` VARCHAR(30) NOT NULL,

    PRIMARY KEY (`id`),

    CONSTRAINT `fk_player_character_account_id`
        FOREIGN KEY (`account_id`)
        REFERENCES `account`(`id`)
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT `uq_player_character_account_id_battlenet_id`
        UNIQUE (`account_id`, `battlenet_id`),

    INDEX `ix_player_character_battlenet_id` (`battlenet_id`),
    INDEX `ix_player_character_name` (`name`)

);

CREATE TABLE `season`
(

    `id` SERIAL,
    `region` TINYINT UNSIGNED NOT NULL,
    `battlenet_id` BIGINT UNSIGNED NOT NULL,
    `year` MEDIUMINT UNSIGNED NOT NULL,
    `number` TINYINT UNSIGNED NOT NULL,

    PRIMARY KEY (`id`),

    CONSTRAINT `uq_season_region_battlenet_id`
        UNIQUE (`region`, `battlenet_id`)
);

CREATE TABLE `league`
(

    `id` SERIAL,
    `season_id` BIGINT UNSIGNED NOT NULL,
    `type` TINYINT UNSIGNED NOT NULL,
    `queue_type` TINYINT UNSIGNED NOT NULL,
    `team_type` TINYINT UNSIGNED NOT NULL,

    PRIMARY KEY (`id`),

    CONSTRAINT `fk_league_season_id`
        FOREIGN KEY (`season_id`)
        REFERENCES `season`(`id`)
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT `uq_league_season_id_type_queue_type_team_type`
        UNIQUE (`season_id`, `type`, `queue_type`, `team_type`)

);

CREATE TABLE `league_tier`
(

    `id` SERIAL,
    `league_id` BIGINT UNSIGNED NOT NULL,
    `type` TINYINT UNSIGNED NOT NULL,
    `min_rating` MEDIUMINT UNSIGNED,
    `max_rating` MEDIUMINT UNSIGNED,

    PRIMARY KEY (`id`),

    CONSTRAINT `fk_league_tier_league_id`
        FOREIGN KEY (`league_id`)
        REFERENCES `league`(`id`)
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT `uq_league_tier_league_id_type`
        UNIQUE (`league_id`, `type`)

);

CREATE TABLE `division`
(

    `id` SERIAL,
    `league_tier_id` BIGINT UNSIGNED NOT NULL,
    `battlenet_id` BIGINT UNSIGNED NOT NULL,

    PRIMARY KEY (`id`),

    CONSTRAINT `fk_division_league_tier_id`
        FOREIGN KEY (`league_tier_id`)
        REFERENCES `league_tier`(`id`)
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT `uq_division_league_tier_id_battlenet_id`
        UNIQUE(`league_tier_id`, `battlenet_id`)

);

CREATE TABLE `team`
(

    `id` SERIAL,
    `division_id` BIGINT UNSIGNED NOT NULL,
    `battlenet_id` BIGINT UNSIGNED NOT NULL,
    `season` BIGINT UNSIGNED NOT NULL,
    `region` TINYINT UNSIGNED NOT NULL,
    `league_type` TINYINT UNSIGNED NOT NULL,
    `queue_type` TINYINT UNSIGNED NOT NULL,
    `team_type` TINYINT UNSIGNED NOT NULL,
    `tier_type` TINYINT UNSIGNED NOT NULL,
    `rating` MEDIUMINT UNSIGNED NOT NULL,
    `points` MEDIUMINT UNSIGNED NOT NULL,
    `wins` MEDIUMINT UNSIGNED NOT NULL,
    `losses` MEDIUMINT UNSIGNED NOT NULL,
    `ties` MEDIUMINT UNSIGNED NOT NULL,

    PRIMARY KEY (`id`),

    CONSTRAINT `fk_team_division_id`
        FOREIGN KEY (`division_id`)
        REFERENCES `division`(`id`)
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT `uq_team_division_id_battlenet_id`
        UNIQUE (`division_id`, `battlenet_id`),

    INDEX `ix_team_ladder_search_ix` (season, region, league_type, queue_type, team_type, rating, `id`)

);

CREATE TABLE `team_member`
(
    `team_id` BIGINT UNSIGNED NOT NULL,
    `player_character_id` BIGINT UNSIGNED NOT NULL,
    `terran_games_played` MEDIUMINT UNSIGNED,
    `protoss_games_played` MEDIUMINT UNSIGNED,
    `zerg_games_played` MEDIUMINT UNSIGNED,
    `random_games_played` MEDIUMINT UNSIGNED,

    PRIMARY KEY (`team_id`, `player_character_id`),

    CONSTRAINT `fk_team_member_team_id`
        FOREIGN KEY (`team_id`)
        REFERENCES `team`(`id`)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_team_player_character_id`
        FOREIGN KEY (`player_character_id`)
        REFERENCES `player_character`(`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE `league_stats`
(
    `league_id` BIGINT UNSIGNED NOT NULL,
    `player_count` MEDIUMINT UNSIGNED NOT NULL,
    `team_count` MEDIUMINT UNSIGNED NOT NULL,
    `terran_games_played` MEDIUMINT UNSIGNED NOT NULL,
    `protoss_games_played` MEDIUMINT UNSIGNED NOT NULL,
    `zerg_games_played` MEDIUMINT UNSIGNED NOT NULL,
    `random_games_played` MEDIUMINT UNSIGNED NOT NULL,

    PRIMARY KEY (`league_id`),

    CONSTRAINT `fk_league_stats_league_id`
        FOREIGN KEY (`league_id`)
        REFERENCES `league`(`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
);
