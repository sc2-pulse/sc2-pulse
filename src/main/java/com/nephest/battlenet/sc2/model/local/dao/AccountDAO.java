// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.config.security.SC2PulseAuthority;
import com.nephest.battlenet.sc2.model.BasePlayerCharacter;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.util.PostgreSQLUtils;
import com.nephest.battlenet.sc2.web.service.BlizzardPrivacyService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class AccountDAO
{
    public static final String STD_SELECT =
        "account.id AS \"account.id\", "
        + "account.partition AS \"account.partition\", "
        + "account.battle_tag AS \"account.battle_tag\", "
        + "account.hidden AS \"account.hidden\" ";

    private static final String CREATE_QUERY = "INSERT INTO account "
        + "(partition, battle_tag) "
        + "VALUES (:partition, :battleTag)";

    private static final String MERGE_QUERY =
        "WITH selected AS ("
            + "SELECT id FROM account WHERE partition = :partition AND battle_tag = :battleTag "
        + "), "
        + "inserted AS ("
            + "INSERT INTO account "
            + "(partition, battle_tag) "
            + "SELECT :partition, :battleTag "
            + "WHERE NOT EXISTS(SELECT 1 FROM selected) "
            + "ON CONFLICT(partition, battle_tag) DO UPDATE SET "
            + "partition=excluded.partition "
            + "RETURNING id"
        + ") "
        + "SELECT id FROM selected "
        + "UNION "
        + "SELECT id FROM inserted";

    private static final String MERGE_WITH_ACCOUNT_QUERY =
        "WITH selected AS "
        + "("
            + "SELECT id FROM account WHERE partition = :partition AND battle_tag = :battleTag "
        + "), "
        + "selected_by_character AS "
        + "("
            + "SELECT account.id "
            + "FROM player_character "
            + "INNER JOIN account ON account.id = player_character.account_id "
            + "WHERE player_character.region = :region "
            + "AND player_character.realm = :realm "
            + "AND player_character.battlenet_id = :battlenetId "
        + "), "
        + "updated AS "
        + "("
            + "UPDATE account "
            + "SET battle_tag = :battleTag "
            + "FROM selected_by_character "
            + "WHERE NOT EXISTS(SELECT 1 FROM selected) "
            + "AND selected_by_character.id = account.id "
            + "AND battle_tag != :battleTag "
            + "AND account.anonymous IS NULL "
        + "), "
        + "inserted AS"
        + " ("
            + "INSERT INTO account "
            + "(partition, battle_tag) "
            + "SELECT :partition, :battleTag "
            + "WHERE NOT EXISTS(SELECT 1 FROM selected) "
            + "AND NOT EXISTS(SELECT 1 FROM selected_by_character) "
            + "ON CONFLICT(partition, battle_tag) DO UPDATE SET "
            + "partition=excluded.partition "
            + "RETURNING id"
        + ") "
        + "SELECT id FROM selected "
        + "UNION "
        + "SELECT id FROM selected_by_character "
        + "UNION "
        + "SELECT id FROM inserted";

    private static final String ANONYMIZE_EXPIRED_ACCOUNTS =
        "UPDATE account "
        + "SET battle_tag = '" + BasePlayerCharacter.DEFAULT_FAKE_NAME + "#' "
        + "|| player_character.region::text || player_character.realm::text || player_character.battlenet_id::text "
        + "FROM player_character "
        + "WHERE account.updated >= :from "
        + "AND account.updated < NOW() - INTERVAL '" + BlizzardPrivacyService.DATA_TTL.toDays() + " days' "
        + "AND account.id = player_character.account_id";

    private static final String UPDATE_UPDATED =
        "UPDATE account "
        + "SET updated = :updated "
        + "WHERE id IN(:ids)";

    private static final String FIND_UPDATED_BY_ID =
        "SELECT updated "
        + "FROM account "
        + "WHERE id = :id";

    private static final String UPDATE_ANONYMOUS_FLAG =
        "UPDATE account "
        + "SET anonymous = :anonymous "
        + "WHERE id = :id";

    private static final String FIND_ANONYMOUS_FLAG_BY_ID =
        "SELECT anonymous "
        + "FROM account "
        + "WHERE id = :id";

    private static final String REMOVE_EMPTY_ACCOUNTS =
        "DELETE FROM account a "
        + "USING account b "
        + "LEFT JOIN player_character ON b.id = player_character.account_id "
        + "WHERE "
        + "a.id = b.id "
        + "AND player_character.id IS NULL";

    private static final String FIND_BY_PARTITION_AND_BATTLE_TAG =
        "SELECT " + STD_SELECT
        + "FROM account "
        + "WHERE partition = :partition "
        + "AND battle_tag = :battleTag";

    private static final String FIND_BY_DISCORD_USER_ID =
        "SELECT " + STD_SELECT
        + "FROM account_discord_user "
        + "INNER JOIN account ON account_discord_user.account_id = account.id "
        + "WHERE account_discord_user.discord_user_id = :discordUserId";

    private static final String FIND_BY_IDS =
        "SELECT " + STD_SELECT
        + "FROM account "
        + "WHERE id IN(:ids)";

    private static final String FIND_BY_ROLE =
        "SELECT " + STD_SELECT
        + "FROM account_role "
        + "INNER JOIN account ON account_role.account_id = account.id "
        + "WHERE account_role.role = :role "
        + "ORDER BY account.id";

    private static final String FIND_BATTLE_TAGS_BY_BATTLE_TAG_LIKE =
        "SELECT battle_tag "
        + "FROM account "
        + "INNER JOIN player_character ON account.id = player_character.account_id "
        + "INNER JOIN player_character_stats ON player_character.id = player_character_stats.player_character_id "
        + "WHERE LOWER(battle_tag) LIKE LOWER(:battleTagLike) "
        + "GROUP BY battle_tag "
        + "ORDER BY MAX(rating_max) DESC "
        + "LIMIT :limit";

    private final NamedParameterJdbcTemplate template;
    private final ConversionService conversionService;

    private static RowMapper<Account> STD_ROW_MAPPER;
    private static ResultSetExtractor<Account> STD_EXTRACTOR;

    @Autowired
    public AccountDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        this.template = template;
        this.conversionService = conversionService;
        initMappers(conversionService);
    }

    private void initMappers(ConversionService conversionService)
    {
        if(STD_ROW_MAPPER == null) STD_ROW_MAPPER = (rs, num)-> new Account
        (
            rs.getLong("account.id"),
            conversionService.convert(rs.getInt("account.partition"), Partition.class),
            rs.getString("account.battle_tag"),
            DAOUtils.getBoolean(rs, "account.hidden")
        );

        if(STD_EXTRACTOR == null) STD_EXTRACTOR = DAOUtils.getResultSetExtractor(STD_ROW_MAPPER);
    }

    public static RowMapper<Account> getStdRowMapper()
    {
        return STD_ROW_MAPPER;
    }

    public static ResultSetExtractor<Account> getStdExtractor()
    {
        return STD_EXTRACTOR;
    }

    public Account create(Account account)
    {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = createParameterSource(account);
        template.update(CREATE_QUERY, params, keyHolder, new String[]{"id"});
        account.setId(keyHolder.getKey().longValue());
        return account;
    }

    public Account merge(Account account)
    {
        MapSqlParameterSource params = createParameterSource(account);
        account.setId(template.query(MERGE_QUERY, params, DAOUtils.LONG_EXTRACTOR));
        return account;
    }


    public Account merge(Account account, PlayerCharacter playerCharacter)
    {
        MapSqlParameterSource params = createParameterSource(account);
        params.addValue("region", conversionService.convert(playerCharacter.getRegion(), Integer.class))
            .addValue("realm", playerCharacter.getRealm())
            .addValue("battlenetId", playerCharacter.getBattlenetId());
        account.setId(template.query(MERGE_WITH_ACCOUNT_QUERY, params, DAOUtils.LONG_EXTRACTOR));
        return account;
    }


    public int removeEmptyAccounts()
    {
        return template.update(REMOVE_EMPTY_ACCOUNTS, new MapSqlParameterSource());
    }

    public int anonymizeExpiredAccounts(OffsetDateTime from)
    {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("from", from);
        return template.update(ANONYMIZE_EXPIRED_ACCOUNTS, params);
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


    public int updateAnonymousFlag(Long id, Boolean anonymous)
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

    public Optional<Account> find(Partition partition, String battleTag)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("partition", conversionService.convert(partition, Integer.class))
            .addValue("battleTag", battleTag);
        return Optional.ofNullable(template.query(FIND_BY_PARTITION_AND_BATTLE_TAG, params, STD_EXTRACTOR));
    }

    public Optional<Account> findByDiscordUserId(Long discordUserId)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("discordUserId", discordUserId);
        return Optional.ofNullable(template.query(FIND_BY_DISCORD_USER_ID, params, STD_EXTRACTOR));
    }

    public List<Account> findByIds(Set<Long> ids)
    {
        if(ids.isEmpty()) return List.of();

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("ids", ids);
        return template.query(FIND_BY_IDS, params, STD_ROW_MAPPER);
    }

    public List<Account> findByRole(SC2PulseAuthority authority)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("role", conversionService.convert(authority, Integer.class));
        return template.query(FIND_BY_ROLE, params, STD_ROW_MAPPER);
    }

    public List<String> findBattleTags(String battleTagLike, int limit)
    {
        battleTagLike = PostgreSQLUtils.escapeLikePattern(battleTagLike) + "%";
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("battleTagLike", battleTagLike)
            .addValue("limit", limit);
        return template.queryForList(FIND_BATTLE_TAGS_BY_BATTLE_TAG_LIKE, params, String.class);
    }

    private MapSqlParameterSource createParameterSource(Account account)
    {
        return new MapSqlParameterSource()
            .addValue("partition", conversionService.convert(account.getPartition(), Integer.class))
            .addValue("battleTag", account.getBattleTag());
    }

}

