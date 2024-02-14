// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.BasePlayerCharacter;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.util.BookmarkedResult;
import com.nephest.battlenet.sc2.model.util.PostgreSQLUtils;
import com.nephest.battlenet.sc2.model.util.SimpleBookmarkedResultSetExtractor;
import com.nephest.battlenet.sc2.web.service.BlizzardPrivacyService;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple4;

@Repository
public class PlayerCharacterDAO
{
    public static final String STD_SELECT =
        "player_character.id AS \"player_character.id\", "
        + "player_character.account_id AS \"player_character.account_id\", "
        + "player_character.region AS \"player_character.region\", "
        + "player_character.battlenet_id AS \"player_character.battlenet_id\", "
        + "player_character.realm AS \"player_character.realm\", "
        + "player_character.name AS \"player_character.name\" ";

    private static final String CREATE_QUERY = "INSERT INTO player_character "
        + "(account_id, region, battlenet_id, realm, name) "
        + "VALUES (:accountId, :region, :battlenetId, :realm, :name)";

    private static final String REBOUND =
        "rebound AS "
        + "("
            + "SELECT updated.id, "
            + "updated.account_id AS new_account_id, "
            + "selected.account_id AS old_account_id "
            + "FROM updated "
            + "INNER JOIN selected USING(id) "
            + "WHERE updated.account_id != selected.account_id"
        + "), "
        + "rebound_pro_players AS "
        + "("
            + "UPDATE pro_player_account "
            + "SET account_id = rebound.new_account_id "
            + "FROM rebound "
            + "WHERE pro_player_account.account_id = rebound.old_account_id "
            + "AND NOT EXISTS "
            + "("
                + "SELECT 1 FROM pro_player_account WHERE account_id = rebound.new_account_id "
            + ")"
        + "), "
        + "rebound_account_following AS "
        + "("
            + "UPDATE account_following "
            + "SET account_id = "
                + "CASE "
                    + "WHEN account_id = rebound.old_account_id "
                    + "THEN rebound.new_account_id "
                    + "ELSE account_id "
                + "END, "
            + "following_account_id = "
                + "CASE "
                    + "WHEN following_account_id = rebound.old_account_id "
                    + "THEN rebound.new_account_id "
                    + "ELSE following_account_id "
                + "END "
            + "FROM rebound "
            + "WHERE "
            + "("
                + "account_following.account_id = rebound.old_account_id "
                + "OR account_following.following_account_id = rebound.old_account_id "
            + ") "
            + "AND NOT EXISTS "
            + "("
                + "SELECT 1 "
                + "FROM account_following af "
                + "WHERE af.account_id = "
                    + "CASE "
                        + "WHEN account_following.account_id = rebound.old_account_id "
                        + "THEN rebound.new_account_id "
                        + "ELSE account_following.account_id "
                    + "END "
                + "AND af.following_account_id = "
                    + "CASE "
                        + "WHEN account_following.following_account_id = rebound.old_account_id "
                        + "THEN rebound.new_account_id "
                        + "ELSE account_following.following_account_id "
                    + "END "
            + ")"
        + "), "
        + "rebound_account_role AS "
        + "("
            + "UPDATE account_role "
            + "SET account_id = rebound.new_account_id "
            + "FROM rebound "
            + "WHERE account_role.account_id = rebound.old_account_id "
            + "AND NOT EXISTS "
            + "("
                + "SELECT 1 "
                + "FROM account_role ar "
                + "WHERE ar.account_id = rebound.new_account_id "
                + "AND ar.role = account_role.role"
            + ")"
        + "), "
        + "rebound_evidence_reporter AS "
        + "("
            + "UPDATE evidence "
            + "SET reporter_account_id = rebound.new_account_id "
            + "FROM rebound "
            + "WHERE evidence.reporter_account_id = rebound.old_account_id "
        + "), "
        + "rebound_evidence_vote AS "
        + "("
            + "UPDATE evidence_vote "
            + "SET voter_account_id = rebound.new_account_id "
            + "FROM rebound "
            + "WHERE evidence_vote.voter_account_id = rebound.old_account_id "
            + "AND NOT EXISTS "
            + "("
                + "SELECT 1 "
                + "FROM evidence_vote ev "
                + "WHERE ev.voter_account_id = rebound.new_account_id "
                + "AND ev.evidence_id = evidence_vote.evidence_id "
            + ")"
        + "), "
        + "rebound_discord_user AS "
        + "("
            + "UPDATE account_discord_user "
            + "SET account_id = rebound.new_account_id "
            + "FROM rebound "
            + "WHERE account_discord_user.account_id = rebound.old_account_id "
            + "AND NOT EXISTS "
            + "("
                + "SELECT 1 "
                + "FROM account_discord_user adu "
                + "WHERE adu.account_id = rebound.new_account_id "
            + ")"
        + ") ";

    private static final String MERGE_QUERY =
        "WITH "
        + "vals AS (VALUES(:accountId, :region, :battlenetId, :realm, :name)), "
        + "selected AS "
        + "("
            + "SELECT id, region, realm, battlenet_id, player_character.account_id "
            + "FROM player_character "
            + "INNER JOIN vals v(account_id, region, battlenet_id, realm, name) "
                + "USING (region, realm, battlenet_id)"
        + "), "
        + "updated AS "
        + "("
            + "UPDATE player_character "
            + "SET account_id=v.account_id, "
            + "name=v.name, "
            + "updated=NOW() "
            + "FROM selected "
            + "INNER JOIN vals v(account_id, region, battlenet_id, realm, name) "
                + "USING (region, realm, battlenet_id) "
            + "WHERE player_character.id = selected.id "
            + "AND "
            + "("
                + "player_character.account_id != v.account_id "
                + "OR player_character.name != v.name "
            + ") "
            + "AND player_character.anonymous IS NULL "
            + "RETURNING player_character.id, player_character.account_id "
        + "), "
        + REBOUND + ", "
        + "inserted AS "
        + "("
            + "INSERT INTO player_character "
            + "(account_id, region, battlenet_id, realm, name) "
            + "SELECT * FROM vals "
            + "WHERE NOT EXISTS(SELECT 1 FROM selected) "
            + "ON CONFLICT(region, realm, battlenet_id) DO UPDATE SET "
            + "account_id=excluded.account_id, "
            + "name=excluded.name "
            + "RETURNING id"
        + ") "
        + "SELECT id FROM selected "
        + "UNION "
        + "SELECT id FROM inserted";

    private static final String ID_SELECT =
        "id AS \"player_character.id\", "
        + "region AS \"player_character.region\", "
        + "realm AS \"player_character.realm\", "
        + "battlenet_id AS \"player_character.battlenet_id\" ";

    private static final String UPDATE_CHARACTERS =
        "WITH "
        + "vals AS (VALUES :characters), "
        + "lock_filter AS "
        + "("
            + "SELECT player_character.id, v.* "
            + "FROM vals v(region, realm, battlenet_id, name) "
            + "INNER JOIN player_character USING(region, realm, battlenet_id) "
            + "ORDER BY region, realm, battlenet_id "
            + "FOR UPDATE "
        + "), "
        + "updated AS "
        + "("
            + "UPDATE player_character "
            + "SET updated = NOW(), "
            + "name = v.name "
            + "FROM lock_filter v "
            + "WHERE player_character.id = v.id "
            + "AND player_character.anonymous IS NULL"
        + ") "
        + "SELECT " + ID_SELECT
        + "FROM lock_filter";

    private static final String UPDATE_ACCOUNTS_AND_CHARACTERS =
        "WITH "
        + "vals AS (VALUES :characters), "
        + "selected AS "
        + "("
            + "SELECT player_character.id, player_character.account_id, v.* "
            + "FROM vals v(partition, battle_tag, region, realm, battlenet_id, name, fresh, season) "
            + "INNER JOIN player_character USING(region, realm, battlenet_id) "
            + "ORDER BY region, realm, battlenet_id "
            + "FOR UPDATE "
        + "), "
        + "updated AS "
        + "( "
            + "UPDATE player_character "
            + "SET updated = "
            + "CASE "
                + "WHEN v.fresh "
                + "THEN NOW() "
                + "ELSE "
                    + "CASE "
                        + "WHEN "
                        + "substring(v.name, 0, position('#' in v.name)) = "
                        + "substring(player_character.name, 0, position('#' in player_character.name)) "
                        + "THEN NOW() "
                        + "ELSE player_character.updated "
                    + "END "
            + "END, "
            /*
                There can be a situation when a BattleTag already exists in one region, but characters were not
                yet rebound to it in another region due to API issues. Rebind it now.
             */
            + "account_id = COALESCE(account.id, v.account_id), "
            + "name = CASE WHEN v.fresh THEN v.name ELSE player_character.name END "
            + "FROM selected v "
            + "LEFT JOIN account ON v.partition = account.partition "
                + "AND v.battle_tag = account.battle_tag "
            + "WHERE player_character.id = v.id "
            + "AND player_character.anonymous IS NULL "
            + "RETURNING player_character.id, player_character.account_id, v.battle_tag, v.season "
        + "), "
        + REBOUND + ", "
        + "account_lock_filter AS "
        + "( "
            + "SELECT account.id, "
            + "updated.battle_tag, "
            + "updated.season "
            + "FROM updated "
            + "INNER JOIN account ON updated.account_id = account.id "
            + "ORDER BY partition, battle_tag "
            + "FOR UPDATE "
        + "), "
        + "updated_account AS "
        + "( "
            + "UPDATE account "
            + "SET updated = "
                + "CASE "
                    + "WHEN account.battle_tag_last_season <= account_lock_filter.season "
                    + "OR account.battle_tag = account_lock_filter.battle_tag "
                    + "THEN NOW() "
                    + "ELSE account.updated "
                + "END, "
            + "battle_tag = "
                + "CASE "
                    + "WHEN account.battle_tag_last_season <= account_lock_filter.season "
                    + "THEN account_lock_filter.battle_tag "
                    + "ELSE account.battle_tag "
                + "END, "
            + "battle_tag_last_season = GREATEST(account.battle_tag_last_season, account_lock_filter.season) "
            + "FROM account_lock_filter "
            + "WHERE account.id = account_lock_filter.id "
            + "AND account.anonymous IS NULL"
        + ") "
        + "SELECT " + ID_SELECT
        + "FROM selected";

    private static final String UPDATE_ANONYMOUS_FLAG =
        "UPDATE player_character "
        + "SET anonymous = :anonymous "
        + "WHERE id = :id";

    private static final String FIND_ANONYMOUS_FLAG_BY_ID =
        "SELECT anonymous "
        + "FROM player_character "
        + "WHERE id = :id";

    private static final String ANONYMIZE_EXPIRED_CHARACTERS =
        "UPDATE player_character "
        + "SET name = '" + BasePlayerCharacter.DEFAULT_FAKE_FULL_NAME + "' "
        + "WHERE updated >= :from "
        + "AND updated < NOW() - INTERVAL '" + BlizzardPrivacyService.DATA_TTL.toDays() + " days' ";

    private static final String UPDATE_UPDATED =
        "UPDATE player_character "
        + "SET updated = :updated "
        + "WHERE id IN(:ids)";

    private static final String FIND_UPDATED_BY_ID =
        "SELECT updated "
        + "FROM player_character "
        + "WHERE id = :id";

    private static final String FIND_PRO_PLAYER_CHARACTER_IDS =
        "SELECT player_character.id FROM player_character "
        + "INNER JOIN account ON player_character.account_id = account.id "
        + "INNER JOIN pro_player_account ON account.id = pro_player_account.account_id "
        + "ORDER BY player_character.id";

    private static final String FIND_PLAYER_CHARACTER_IDS_BY_PRO_PLAYER_IDS =
        "SELECT player_character.id "
        + "FROM player_character "
        + "INNER JOIN account ON player_character.account_id = account.id "
        + "INNER JOIN pro_player_account ON account.id = pro_player_account.account_id "
        + "WHERE pro_player_account.pro_player_id IN (:proPlayerIds)";

    private static final String FIND_PRO_PLAYER_CHARACTERS =
        "SELECT " + PlayerCharacterDAO.STD_SELECT + " FROM player_character "
        + "INNER JOIN account ON player_character.account_id = account.id "
        + "INNER JOIN pro_player_account ON account.id = pro_player_account.account_id "
        + "ORDER BY player_character.id";

    private static final String FIND_TOP_PLAYER_CHARACTERS =
        "SELECT "
        + "team.id AS \"team.id\", team.rating AS \"team.rating\", "
        + PlayerCharacterDAO.STD_SELECT + " FROM player_character "
        + "INNER JOIN team_member ON player_character.id = team_member.player_character_id "
        + "INNER JOIN team ON team_member.team_id = team.id "

        + "WHERE team.season = :season AND team.region IS NOT NULL AND team.league_type IS NOT NULL "
        + "AND team.queue_type = :queueType AND team.team_type = :teamType "
        + "AND (team.rating, team.id) < (:ratingAnchor, :idAnchor) "

        + "ORDER BY team.rating DESC, team.id DESC, player_character.id DESC "
        + "LIMIT :limit";

    private static final String FIND_RECENTLY_ACTIVE_CHARACTERS =
        "WITH team_filter AS "
        + "( "
            + "SELECT DISTINCT team_id "
            + "FROM team_state "
            + "WHERE \"timestamp\" >= :point "
        + ") "
        + "SELECT DISTINCT ON(player_character.id) "
        + STD_SELECT
        + "FROM team_filter "
        + "INNER JOIN team_member USING(team_id) "
        + "INNER JOIN player_character ON team_member.player_character_id = player_character.id "
        + "WHERE player_character.region IN (:regions)";

    private static final String FIND_TOP_RECENTLY_ACTIVE_CHARACTERS =
        "WITH team_filter AS "
        + "( "
            + "SELECT DISTINCT team.id "
            + "FROM team_state "
            + "INNER JOIN team ON team_state.team_id = team.id "
            + "WHERE \"timestamp\" >= :point "
            + "AND team.region IN(:regions) "
            + "AND team.queue_type = :queueType "
            + "AND team.team_type = :teamType "
        + "), "
        + "top_team_filter AS "
        + "("
            + "SELECT id "
            + "FROM team_filter "
            + "INNER JOIN team USING(id) "
            + "ORDER BY rating DESC "
            + "LIMIT :teamLimit"
        + ") "
        + "SELECT DISTINCT ON(player_character.id) "
        + STD_SELECT
        + "FROM top_team_filter "
        + "INNER JOIN team_member ON top_team_filter.id = team_member.team_id "
        + "INNER JOIN player_character ON team_member.player_character_id = player_character.id "
        + "LIMIT :limit";

    private static final String FIND_BY_REGION_AND_REALM_AND_BATTLENET_ID = "SELECT " + STD_SELECT
        + "FROM player_character "
        + "WHERE region=:region "
        + "AND realm=:realm "
        + "AND battlenet_id=:battlenetId";

    private static final String FIND_BY_UPDATED_AND_ID_MAX_EXCLUDED =
        "SELECT " + STD_SELECT
        + "FROM player_character "
        + "WHERE updated < :updatedMax "
        + "AND id < :idMax "
        + "AND (array_length(:regions::smallint[], 1) IS NULL OR region = ANY(:regions)) "
        + "ORDER BY id DESC "
        + "LIMIT :limit";

    private static final String FIND_NAMES_WITHOUT_DISCRIMINATOR_BY_NAME_LIKE =
        "WITH character_filter AS "
        + "("
            + "SELECT player_character.name, MAX(rating_max) AS rating_max "
            + "FROM player_character "
            + "INNER JOIN player_character_stats ON player_character.id = player_character_stats.player_character_id "
            + "WHERE LOWER(name) LIKE LOWER(:nameLike) "
            + "GROUP BY player_character.name "
            + "ORDER BY rating_max DESC "
            + "LIMIT :limit * 3 "
        + ") "
        + "SELECT substring(name from '^.*(?=(#))') AS sub_name "
        + "FROM character_filter "
        + "GROUP BY sub_name "
        + "ORDER BY MAX(rating_max) DESC "
        + "LIMIT :limit";

    private static final String FIND_BY_IDS =
        "SELECT " + STD_SELECT
        + "FROM player_character "
        + "WHERE id IN(:ids)";

    private static final String FIND_BY_ACCOUNT_ID =
        "SELECT " + STD_SELECT
        + "FROM player_character "
        + "WHERE account_id = :accountId";

    private static final String FIND_INACTIVE_CLAN_MEMBERS =
        "SELECT " + STD_SELECT
        + "FROM clan_member "
        + "INNER JOIN player_character ON clan_member.player_character_id = player_character.id "
        + "WHERE clan_member.updated < :to "
        + "AND clan_member.player_character_id < :idCursor "
        + "ORDER BY clan_member.player_character_id DESC "
        + "LIMIT :limit";

    private static final String COUNT_BY_UPDATED_MAX =
        "SELECT COUNT(*) "
        + "FROM player_character "
        + "WHERE updated <= :updatedMax "
        + "AND (array_length(:regions::smallint[], 1) IS NULL OR region = ANY(:regions)) ";

    private static final String FIND_IDS_BY_ACCOUNT_IDS =
        "SELECT id "
        + "FROM player_character "
        + "WHERE account_id IN(:accountIds)";


    private static RowMapper<PlayerCharacter> STD_ROW_MAPPER;
    private static ResultSetExtractor<PlayerCharacter> STD_EXTRACTOR;
    private static RowMapper<PlayerCharacter> ID_ROW_MAPPER;
    private static ResultSetExtractor<PlayerCharacter> ID_EXTRACTOR;
    private static ResultSetExtractor<BookmarkedResult<List<PlayerCharacter>>> BOOKMARKED_STD_ROW_EXTRACTOR;

    private final NamedParameterJdbcTemplate template;
    private final ConversionService conversionService;

    @Autowired
    public PlayerCharacterDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        this.template = template;
        this.conversionService = conversionService;
        initMappers(conversionService);
    }

    private static void initMappers(ConversionService conversionService)
    {
        if(STD_ROW_MAPPER == null) STD_ROW_MAPPER = (rs, i)-> new PlayerCharacter
        (
            rs.getLong("player_character.id"),
            rs.getLong("player_character.account_id"),
            conversionService.convert(rs.getInt("player_character.region"), Region.class),
            rs.getLong("player_character.battlenet_id"),
            rs.getInt("player_character.realm"),
            rs.getString("player_character.name")
        );
        if(ID_ROW_MAPPER == null) ID_ROW_MAPPER = (rs, i)-> new PlayerCharacter
        (
            rs.getLong("player_character.id"),
            null,
            conversionService.convert(rs.getInt("player_character.region"), Region.class),
            rs.getLong("player_character.battlenet_id"),
            rs.getInt("player_character.realm"),
            null
        );

        if(STD_EXTRACTOR == null) STD_EXTRACTOR = DAOUtils.getResultSetExtractor(STD_ROW_MAPPER);
        if(ID_EXTRACTOR == null) ID_EXTRACTOR = DAOUtils.getResultSetExtractor(ID_ROW_MAPPER);

        if(BOOKMARKED_STD_ROW_EXTRACTOR == null) BOOKMARKED_STD_ROW_EXTRACTOR
            = new SimpleBookmarkedResultSetExtractor<>(STD_ROW_MAPPER, "team.rating", "team.id");
    }

    public static RowMapper<PlayerCharacter> getStdRowMapper()
    {
        return STD_ROW_MAPPER;
    }

    public static ResultSetExtractor<PlayerCharacter> getStdExtractor()
    {
        return STD_EXTRACTOR;
    }

    public static RowMapper<PlayerCharacter> getIdRowMapper()
    {
        return ID_ROW_MAPPER;
    }

    public static ResultSetExtractor<PlayerCharacter> getIdExtractor()
    {
        return ID_EXTRACTOR;
    }

    public PlayerCharacter create(PlayerCharacter character)
    {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = createParameterSource(character);
        template.update(CREATE_QUERY, params, keyHolder, new String[]{"id"});
        character.setId(keyHolder.getKey().longValue());
        return character;
    }

    public PlayerCharacter merge(PlayerCharacter character)
    {
        MapSqlParameterSource params = createParameterSource(character);
        character.setId(template.query(MERGE_QUERY, params, DAOUtils.LONG_EXTRACTOR));
        return character;
    }

    /*
        updateCharacters and updateAccountsAndCharacters methods are primarily used to update historical BattleTags,
        names, and timestamps. This ensures full compliance with the Blizzard ToS.
     */
    public Set<PlayerCharacter> updateCharacters(Set<PlayerCharacter> characters)
    {
        if(characters.isEmpty()) return Set.of();

        List<Object[]> data = characters.stream()
            .map(c->new Object[]
            {
                conversionService.convert(c.getRegion(), Integer.class),
                c.getRealm(),
                c.getBattlenetId(),
                c.getName(),
            })
            .collect(Collectors.toList());
        SqlParameterSource params = new MapSqlParameterSource().addValue("characters", data);
        List<PlayerCharacter> ids = template.query(UPDATE_CHARACTERS, params, ID_ROW_MAPPER);
        return DAOUtils.updateOriginals
        (
            characters,
            ids,
            (o, m)->o.setId(m.getId()),
            c->c.setId(null)
        );
    }

    public Set<PlayerCharacter> updateAccountsAndCharacters
    (Set<Tuple4<Account, PlayerCharacter, Boolean, Integer>> accountsAndCharacters)
    {
        if(accountsAndCharacters.isEmpty()) return Set.of();

        List<Object[]> data = accountsAndCharacters.stream()
            .map(c->new Object[]
            {
                conversionService.convert(c.getT1().getPartition(), Integer.class),
                c.getT1().getBattleTag(),
                conversionService.convert(c.getT2().getRegion(), Integer.class),
                c.getT2().getRealm(),
                c.getT2().getBattlenetId(),
                c.getT2().getName(),
                c.getT3(),
                c.getT4()
            })
            .collect(Collectors.toList());
        SqlParameterSource params = new MapSqlParameterSource().addValue("characters", data);
        List<PlayerCharacter> ids = template.query(UPDATE_ACCOUNTS_AND_CHARACTERS, params, ID_ROW_MAPPER);
        Set<PlayerCharacter> characters = accountsAndCharacters.stream()
            .map(Tuple2::getT2)
            .collect(Collectors.toSet());
        return DAOUtils.updateOriginals
        (
            characters,
            ids,
            (o, m)->o.setId(m.getId()),
            c->c.setId(null)
        );
    }

    public int updateAnonymousFlag( Long id, Boolean anonymous)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("anonymous", anonymous ? true : null);
        return template.update(UPDATE_ANONYMOUS_FLAG, params);
    }

    public boolean getAnonymousFlag(Long id)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", id);
        Boolean anonymous = template.queryForObject(FIND_ANONYMOUS_FLAG_BY_ID, params, Boolean.class);
        return anonymous != null && anonymous;
    }

    public int anonymizeExpiredCharacters(OffsetDateTime from)
    {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("from", from);
        return template.update(ANONYMIZE_EXPIRED_CHARACTERS, params);
    }

    public int updateUpdated(OffsetDateTime updated, Set<Long> ids)
    {
        if(ids.isEmpty()) return 0;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("ids", ids)
            .addValue("updated", updated);
        return template.update(UPDATE_UPDATED, params);
    }

    //for tests
    public OffsetDateTime getUpdated(Long id)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", id);
        return template.queryForObject(FIND_UPDATED_BY_ID, params, OffsetDateTime.class);
    }

    private MapSqlParameterSource createParameterSource(PlayerCharacter character)
    {
        return new MapSqlParameterSource()
            .addValue("accountId", character.getAccountId())
            .addValue("region", conversionService.convert(character.getRegion(), Integer.class))
            .addValue("battlenetId", character.getBattlenetId())
            .addValue("realm", character.getRealm())
            .addValue("name", character.getName());
    }

    @Cacheable(cacheNames = "pro-player-characters")
    public List<Long> findProPlayerCharacterIds()
    {
        return template.query(FIND_PRO_PLAYER_CHARACTER_IDS, DAOUtils.LONG_MAPPER);
    }

    public List<Long> findCharacterIdsByProPlayerIds(Set<Long> proPlayerIds)
    {
        if(proPlayerIds.isEmpty()) return List.of();

        MapSqlParameterSource params = new MapSqlParameterSource("proPlayerIds", proPlayerIds);
        return template.query
        (
            FIND_PLAYER_CHARACTER_IDS_BY_PRO_PLAYER_IDS,
            params,
            DAOUtils.LONG_MAPPER
        );
    }

    public List<PlayerCharacter> findProPlayerCharacters()
    {
        return template.query(FIND_PRO_PLAYER_CHARACTERS, PlayerCharacterDAO.getStdRowMapper());
    }

    public BookmarkedResult<List<PlayerCharacter>> findTopPlayerCharacters
    (
        int season,
        QueueType queueType,
        TeamType teamType,
        int count,
        BookmarkedResult<List<PlayerCharacter>> bookmarkedResult
    )
    {
        Long[] bookmark = bookmarkedResult == null ? null : bookmarkedResult.getBookmark();
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("season", season)
            .addValue("queueType", conversionService.convert(queueType, Integer.class))
            .addValue("teamType", conversionService.convert(teamType, Integer.class))
            .addValue("limit", count)
            .addValue("idAnchor", bookmark != null ? bookmark[1] : 0L)
            .addValue("ratingAnchor", bookmark != null ? bookmark[0] : 99999L);
        return template.query(FIND_TOP_PLAYER_CHARACTERS, params, BOOKMARKED_STD_ROW_EXTRACTOR);
    }

    public List<PlayerCharacter> findRecentlyActiveCharacters(OffsetDateTime from, Region... regions)
    {
        if(regions.length == 0) return List.of();

        List<Integer> regionInts = Arrays.stream(regions)
            .map(r->conversionService.convert(r, Integer.class))
            .collect(Collectors.toList());
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("regions", regionInts)
            .addValue("point", from);
        return template.query(FIND_RECENTLY_ACTIVE_CHARACTERS, params, getStdRowMapper());
    }

    public List<PlayerCharacter> findTopRecentlyActiveCharacters(
        OffsetDateTime from,
        QueueType queueType,
        TeamType teamType,
        Collection<? extends Region> regions,
        int count
    )
    {
        if(regions.isEmpty() || count < 1) return List.of();

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("queueType", conversionService.convert(queueType, Integer.class))
            .addValue("teamType", conversionService.convert(teamType, Integer.class))
            .addValue("regions", regions.stream()
                .map(r->conversionService.convert(r, Integer.class))
                .collect(Collectors.toList()))
            .addValue("point", from)
            .addValue("teamLimit", count / queueType.getTeamFormat().getMemberCount(teamType))
            .addValue("limit", count);
        return template.query(FIND_TOP_RECENTLY_ACTIVE_CHARACTERS, params, getStdRowMapper());
    }

    public Optional<PlayerCharacter> find(Region region, int realm, long battlenetId)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("region", conversionService.convert(region, Integer.class))
            .addValue("realm", realm)
            .addValue("battlenetId", battlenetId);
        return Optional.ofNullable(template.query(FIND_BY_REGION_AND_REALM_AND_BATTLENET_ID, params, getStdExtractor()));
    }

    public List<PlayerCharacter> find
    (
        OffsetDateTime updatedMax,
        Long idMax,
        Set<Region> regions,
        int limit
    )
    {
        Integer[] regionIds = regions.isEmpty()
            ? null
            : regions.stream()
                .map(r->conversionService.convert(r, Integer.class))
                .toArray(Integer[]::new);
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("updatedMax", updatedMax)
            .addValue("idMax", idMax)
            .addValue("regions", regionIds)
            .addValue("limit", limit);
        return template.query(FIND_BY_UPDATED_AND_ID_MAX_EXCLUDED, params, getStdRowMapper());
    }

    public List<PlayerCharacter> find(Set<Long> ids)
    {
        if(ids.isEmpty()) return List.of();

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("ids", ids);
        return template.query(FIND_BY_IDS, params, getStdRowMapper());
    }

    public List<PlayerCharacter> findByAccountId(Long accountId)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("accountId", accountId);
        return template.query(FIND_BY_ACCOUNT_ID, params, getStdRowMapper());
    }

    public List<PlayerCharacter> findInactiveClanMembers
    (
        OffsetDateTime to,
        Long idCursor,
        int limit
    )
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("to", to)
            .addValue("idCursor", idCursor)
            .addValue("limit", limit);
        return template.query(FIND_INACTIVE_CLAN_MEMBERS, params, STD_ROW_MAPPER);
    }

    public int countByUpdatedMax(OffsetDateTime updatedMax, Set<Region> regions)
    {
        Integer[] regionIds = regions.isEmpty()
            ? null
            : regions.stream()
                .map(r->conversionService.convert(r, Integer.class))
                .toArray(Integer[]::new);
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("updatedMax", updatedMax)
            .addValue("regions", regionIds);
        return template.query(COUNT_BY_UPDATED_MAX, params, DAOUtils.INT_EXTRACTOR);
    }

    public List<String> findNamesWithoutDiscriminator(String nameLike, int limit)
    {
        nameLike = PostgreSQLUtils.escapeLikePattern(nameLike) + "%";
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("nameLike", nameLike)
            .addValue("limit", limit);
        return template.queryForList(FIND_NAMES_WITHOUT_DISCRIMINATOR_BY_NAME_LIKE, params, String.class);
    }

    public List<Long> findIdsByAccountIds(Set<Long> accountIds)
    {
        if(accountIds.isEmpty()) return List.of();

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("accountIds", accountIds);
        return template.queryForList(FIND_IDS_BY_ACCOUNT_IDS, params, Long.class);
    }

}
