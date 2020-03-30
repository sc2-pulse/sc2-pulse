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
CREATE TABLE account
(
    id IDENTITY,
    region TINYINT NOT NULL,
    battlenet_id BIGINT NOT NULL,
    battle_tag VARCHAR(30) NOT NULL,
    updated TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_account_region_battlenet_id
        UNIQUE (region, battlenet_id)
);

CREATE INDEX ix_account_battle_tag ON account(battle_tag);
CREATE INDEX ix_account_updated ON account(updated);

CREATE TABLE player_character
(

    id IDENTITY,
    account_id BIGINT NOT NULL,
    battlenet_id BIGINT NOT NULL,
    realm TINYINT NOT NULL,
    name VARCHAR(30) NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT fk_player_character_account_id
        FOREIGN KEY (account_id)
        REFERENCES account(id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT uq_player_character_account_id_battlenet_id
        UNIQUE (account_id, battlenet_id)

);

CREATE INDEX ix_player_character_battlenet_id ON player_character(battlenet_id);
CREATE INDEX ix_player_character_name ON player_character(name);

CREATE TABLE season
(

    id IDENTITY,
    region TINYINT NOT NULL,
    battlenet_id BIGINT NOT NULL,
    year SMALLINT NOT NULL,
    number TINYINT NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT uq_season_region_battlenet_id
        UNIQUE (region, battlenet_id)
);

CREATE TABLE league
(

    id IDENTITY,
    season_id BIGINT NOT NULL,
    type TINYINT NOT NULL,
    queue_type TINYINT NOT NULL,
    team_type TINYINT NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT fk_league_season_id
        FOREIGN KEY (season_id)
        REFERENCES season(id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT uq_league_season_id_type_queue_type_team_type
        UNIQUE (season_id, type, queue_type, team_type)

);

CREATE TABLE league_tier
(

    id IDENTITY,
    league_id BIGINT NOT NULL,
    type TINYINT NOT NULL,
    min_rating SMALLINT,
    max_rating SMALLINT,

    PRIMARY KEY (id),

    CONSTRAINT fk_league_tier_league_id
        FOREIGN KEY (league_id)
        REFERENCES league(id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT uq_league_tier_league_id_type
        UNIQUE (league_id, type)

);

CREATE TABLE division
(

    id IDENTITY,
    league_tier_id BIGINT NOT NULL,
    battlenet_id BIGINT NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT fk_division_league_tier_id
        FOREIGN KEY (league_tier_id)
        REFERENCES league_tier(id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT uq_division_league_tier_id_battlenet_id
        UNIQUE(league_tier_id, battlenet_id)

);

CREATE TABLE team
(

    id IDENTITY,
    division_id BIGINT NOT NULL,
    battlenet_id BIGINT NOT NULL,
    season BIGINT NOT NULL,
    region TINYINT NOT NULL,
    league_type TINYINT NOT NULL,
    queue_type TINYINT NOT NULL,
    team_type TINYINT NOT NULL,
    tier_type TINYINT NOT NULL,
    rating SMALLINT NOT NULL,
    points SMALLINT NOT NULL,
    wins SMALLINT NOT NULL,
    losses SMALLINT NOT NULL,
    ties SMALLINT NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT fk_team_division_id
        FOREIGN KEY (division_id)
        REFERENCES division(id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT uq_team_region_battlenet_id
        UNIQUE (region, battlenet_id)

);

CREATE INDEX ix_team_ladder_search_ix ON team(season, region, league_type, queue_type, team_type, rating, id);

CREATE TABLE team_member
(
    team_id BIGINT NOT NULL,
    player_character_id BIGINT NOT NULL,
    terran_games_played SMALLINT,
    protoss_games_played SMALLINT,
    zerg_games_played SMALLINT,
    random_games_played SMALLINT,

    PRIMARY KEY (team_id, player_character_id),

    CONSTRAINT fk_team_member_team_id
        FOREIGN KEY (team_id)
        REFERENCES team(id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_team_player_character_id
        FOREIGN KEY (player_character_id)
        REFERENCES player_character(id)
        ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE league_stats
(
    league_id BIGINT NOT NULL,
    player_count INTEGER NOT NULL,
    team_count INTEGER NOT NULL,
    terran_games_played INTEGER NOT NULL,
    protoss_games_played INTEGER NOT NULL,
    zerg_games_played INTEGER NOT NULL,
    random_games_played INTEGER NOT NULL,

    PRIMARY KEY (league_id),

    CONSTRAINT fk_league_stats_league_id
        FOREIGN KEY (league_id)
        REFERENCES league(id)
        ON DELETE CASCADE ON UPDATE CASCADE
);
