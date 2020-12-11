// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.local.ProPlayerAccount;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;

@Repository
public class ProPlayerAccountDAO
extends StandardDAO
{

    private static RowMapper<ProPlayerAccount> STD_ROW_MAPPER;

    private static final String CREATE_QUERY =
        "INSERT INTO pro_player_account (pro_player_id, account_id, updated) "
        + "VALUES (:proPlayerId, :accountId, :updated)";
    private static final String MERGE_CLAUSE =
        "ON CONFLICT(account_id) DO UPDATE SET "
        + "pro_player_id=excluded.pro_player_id, "
        + "updated=excluded.updated";
    private static final String MERGE_QUERY = CREATE_QUERY + " " + MERGE_CLAUSE;
    private static final String LINK_BY_BATTLE_TAG_QUERY =
        "INSERT INTO pro_player_account (pro_player_id, account_id, updated) "
        + "SELECT :proPlayerId, account.id, NOW() "
        + "FROM account WHERE battle_tag = :battleTag AND partition = :partition "
        + MERGE_CLAUSE;
    private static final String LINK_BY_PLAYER_CHARACTER_ID_QUERY =
        "INSERT INTO pro_player_account (pro_player_id, account_id, updated) "
        + "SELECT :proPlayerId, account.id, NOW() "
        + "FROM account "
        + "INNER JOIN player_character ON player_character.account_id = account.id "
        + "WHERE partition = :partition AND player_character.id = :playerCharacterId "
        + MERGE_CLAUSE;
    private static final String DELETE_BY_BATTLE_TAG_QUERY =
        "DELETE FROM pro_player_account WHERE pro_player_id = :proPlayerId "
            + "AND account_id = :accountId";


    private final NamedParameterJdbcTemplate template;
    private final ConversionService conversionService;

    @Autowired
    public  ProPlayerAccountDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        super(template, "pro_player_account", "30 DAYS");
        this.template = template;
        this.conversionService = conversionService;
        initMappers();
    }

    private static void initMappers()
    {
        if(STD_ROW_MAPPER == null) STD_ROW_MAPPER = (rs, num)->
        {
            ProPlayerAccount a = new ProPlayerAccount
            (
                rs.getLong("pro_player_account.pro_player_id"),
                rs.getLong("pro_player_account.account_id")
            );
            a.setUpdated(rs.getObject("pro_player_account.updated", OffsetDateTime.class));
            return a;
        };
    }

    public static RowMapper<ProPlayerAccount> getStdRowMapper()
    {
        return STD_ROW_MAPPER;
    }

    private MapSqlParameterSource createParameterSource(ProPlayerAccount proPlayerAccount)
    {
        return new MapSqlParameterSource()
            .addValue("proPlayerId", proPlayerAccount.getProPlayerId())
            .addValue("accountId", proPlayerAccount.getAccountId())
            .addValue("updated", proPlayerAccount.getUpdated());
    }

    public int[] merge(ProPlayerAccount... proPlayerAccounts)
    {
        MapSqlParameterSource[] params = new MapSqlParameterSource[proPlayerAccounts.length];
        for(int i = 0; i < proPlayerAccounts.length; i++)
        {
            proPlayerAccounts[i].setUpdated(OffsetDateTime.now());
            params[i] = createParameterSource(proPlayerAccounts[i]);
        }

        return template.batchUpdate(MERGE_QUERY, params);
    }

    public int[] link(Long proPlayerId, String... battleTags)
    {
        MapSqlParameterSource[] params = new MapSqlParameterSource[battleTags.length];
        for(int i = 0; i < battleTags.length; i++)
            params[i] = new MapSqlParameterSource()
                .addValue("proPlayerId", proPlayerId)
                .addValue("battleTag", battleTags[i])
                //sc2revealed has global partition only
                .addValue("partition", conversionService.convert(Partition.GLOBAL, Integer.class));

        return template.batchUpdate(LINK_BY_BATTLE_TAG_QUERY, params);
    }

    public int[] link(Long proPlayerId, Long... playerCharacterIds)
    {
        MapSqlParameterSource[] params = new MapSqlParameterSource[playerCharacterIds.length];
        for(int i = 0; i < playerCharacterIds.length; i++)
            params[i] = new MapSqlParameterSource()
                .addValue("proPlayerId", proPlayerId)
                .addValue("playerCharacterId", playerCharacterIds[i])
                //sc2revealed has global partition only
                .addValue("partition", conversionService.convert(Partition.GLOBAL, Integer.class));

        return template.batchUpdate(LINK_BY_PLAYER_CHARACTER_ID_QUERY, params);
    }

    public int unlink(Long proPlayerId, Long accountId)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("proPlayerId", proPlayerId)
            .addValue("accountId", accountId);
        return template.update(DELETE_BY_BATTLE_TAG_QUERY, params);
    }

}
