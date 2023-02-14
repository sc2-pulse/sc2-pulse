// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.local.ProPlayerAccount;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProPlayerAccountDAO
{

    public static final String STD_SELECT =
        "pro_player_account.pro_player_id AS \"pro_player_account.pro_player_id\", "
        + "pro_player_account.account_id AS \"pro_player_account.account_id\", "
        + "pro_player_account.revealer_account_id AS \"pro_player_account.revealer_account_id\", "
        + "pro_player_account.updated AS \"pro_player_account.updated\", "
        + "pro_player_account.protected AS \"pro_player_account.protected\" ";

    private static RowMapper<ProPlayerAccount> STD_ROW_MAPPER;

    private static final String CREATE_QUERY =
        "INSERT INTO pro_player_account (pro_player_id, account_id, revealer_account_id, updated, protected) "
        + "VALUES (:proPlayerId, :accountId, :revealerAccountId, :updated, :protected)";
    private static final String MERGE_CLAUSE =
        "ON CONFLICT(account_id) DO UPDATE SET "
        + "revealer_account_id=excluded.revealer_account_id, "
        + "pro_player_id=excluded.pro_player_id, "
        + "updated=excluded.updated, "
        + "protected = excluded.protected ";
    private static final String PROTECTED_MERGE_CLAUSE = MERGE_CLAUSE
        + "WHERE pro_player_account.protected IS NULL";
    private static final String MERGE_QUERY = CREATE_QUERY + " " + MERGE_CLAUSE;
    private static final String PROTECTED_MERGE_QUERY = CREATE_QUERY + PROTECTED_MERGE_CLAUSE;
    private static final String LINK_BY_BATTLE_TAG_QUERY =
        "INSERT INTO pro_player_account (pro_player_id, account_id, updated, protected) "
        + "SELECT :proPlayerId, account.id, NOW(), null "
        + "FROM account WHERE battle_tag = :battleTag AND partition = :partition "
        + PROTECTED_MERGE_CLAUSE;
    private static final String LINK_BY_PLAYER_CHARACTER_ID_QUERY =
        "INSERT INTO pro_player_account (pro_player_id, account_id, updated, protected) "
        + "SELECT :proPlayerId, account.id, NOW(), null "
        + "FROM account "
        + "INNER JOIN player_character ON player_character.account_id = account.id "
        + "WHERE partition = :partition AND player_character.battlenet_id = :playerCharacterBattlenetId "
        + PROTECTED_MERGE_CLAUSE;
    private static final String DELETE_BY_BATTLE_TAG_QUERY =
        "DELETE FROM pro_player_account WHERE pro_player_id = :proPlayerId "
            + "AND account_id = :accountId";
    private static final String FIND_BY_PRO_PLAYER_ID =
        "SELECT " + STD_SELECT
        + "FROM pro_player_account "
        + "WHERE pro_player_id = :proPlayerId";


    private final NamedParameterJdbcTemplate template;
    private final ConversionService conversionService;

    @Autowired
    public  ProPlayerAccountDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        this.template = template;
        this.conversionService = conversionService;
        initMappers();
    }

    private static void initMappers()
    {
        if(STD_ROW_MAPPER == null) STD_ROW_MAPPER = (rs, num)-> new ProPlayerAccount
        (
            rs.getLong("pro_player_account.pro_player_id"),
            rs.getLong("pro_player_account.account_id"),
            DAOUtils.getLong(rs, "pro_player_account.revealer_account_id"),
            rs.getObject("pro_player_account.updated", OffsetDateTime.class),
            rs.getBoolean("pro_player_account.protected")
        );
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
            .addValue("revealerAccountId", proPlayerAccount.getRevealerAccountId())
            .addValue("updated", proPlayerAccount.getUpdated())
            .addValue("protected", proPlayerAccount.isProtected() ? true : null);
    }

    public int[] merge(ProPlayerAccount... proPlayerAccounts)
    {
        return merge(false, proPlayerAccounts);
    }

    /**
     * <p>
     *     Merges pro player links. When isProtected flag is set to true, then links with protected
     *     flag are not updated.
     * </p>
     * @param isProtected protection flag
     * @param proPlayerAccounts links to merge
     * @return number of merged links
     */
    public int[] merge(boolean isProtected, ProPlayerAccount... proPlayerAccounts)
    {
        if(proPlayerAccounts.length == 0) return DAOUtils.EMPTY_INT_ARRAY;

        MapSqlParameterSource[] params = new MapSqlParameterSource[proPlayerAccounts.length];
        for(int i = 0; i < proPlayerAccounts.length; i++)
        {
            proPlayerAccounts[i].setUpdated(OffsetDateTime.now());
            params[i] = createParameterSource(proPlayerAccounts[i]);
        }

        return template.batchUpdate(isProtected ? PROTECTED_MERGE_QUERY : MERGE_QUERY, params);
    }

    /**
     *  <p>Links pro players by BattleTags in protected mode</p>
     * @param proPlayerId pro player id
     * @param battleTags BattleTag array
     * @return number of merged links
     */
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

    /**
     *  <p>Links pro players by character battle net id in protected mode</p>
     * @param proPlayerId pro player id
     * @param playerCharacterBattlenetIds character battle net id array
     * @return number of merged links
     */
    public int[] link(Long proPlayerId, Long... playerCharacterBattlenetIds)
    {
        MapSqlParameterSource[] params = new MapSqlParameterSource[playerCharacterBattlenetIds.length];
        for(int i = 0; i < playerCharacterBattlenetIds.length; i++)
            params[i] = new MapSqlParameterSource()
                .addValue("proPlayerId", proPlayerId)
                .addValue("playerCharacterBattlenetId", playerCharacterBattlenetIds[i])
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

    public List<ProPlayerAccount> findByProPlayerId(Integer id)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("proPlayerId", id);
        return template.query(FIND_BY_PRO_PLAYER_ID, params, STD_ROW_MAPPER);
    }

}
