// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.dao;

import com.nephest.battlenet.sc2.model.local.PlayerCharacterReport;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.ClanDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterReportDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderPlayerCharacterReport;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LadderPlayerCharacterReportDAO
{

    public static final int HIDE_DENIED_REPORTS_DAYS = 30;

    private static final String FIND_REPORTS_TEMPLATE =
        "SELECT "
        + "null AS terran_games_played, "
        + "null AS protoss_games_played, "
        + "null AS zerg_games_played, "
        + "null AS random_games_played, "
        + PlayerCharacterReportDAO.STD_SELECT + ", "
        + PlayerCharacterDAO.STD_SELECT + ", "
        + AccountDAO.STD_SELECT + ", "
        + ClanDAO.STD_SELECT + ", "
        + "pro_player.id AS \"pro_player.id\", "
        + "pro_player.nickname AS \"pro_player.nickname\", "
        + "COALESCE(pro_team.short_name, pro_team.name) AS \"pro_player.team\", "
        + "confirmed_cheater_report.restrictions AS \"confirmed_cheater_report.restrictions\" "
        + "FROM player_character_report "
        + "INNER JOIN player_character ON player_character_report.player_character_id = player_character.id "
        + "INNER JOIN account ON player_character.account_id = account.id "
        + "LEFT JOIN clan_member ON player_character.id = clan_member.player_character_id "
        + "LEFT JOIN clan ON clan_member.clan_id = clan.id "
        + "LEFT JOIN pro_player_account ON account.id=pro_player_account.account_id "
        + "LEFT JOIN pro_player ON pro_player_account.pro_player_id=pro_player.id "
        + "LEFT JOIN pro_team_member ON pro_player.id=pro_team_member.pro_player_id "
        + "LEFT JOIN pro_team ON pro_team_member.pro_team_id=pro_team.id "
        + "LEFT JOIN player_character_report AS confirmed_cheater_report "
            + "ON player_character.id = confirmed_cheater_report.player_character_id "
            + "AND confirmed_cheater_report.type = :cheaterReportType "
            + "AND confirmed_cheater_report.status = true "
            + "%1$s";

    private static final String FIND_REPORTS =
        String.format(FIND_REPORTS_TEMPLATE,
            "WHERE player_character_report.status IS NULL "
            + "OR player_character_report.status = true "
            + "OR player_character_report.status_change_timestamp >= :from");

    private static final String FIND_REPORTS_BY_CHARACTER_IDS =
        String.format(FIND_REPORTS_TEMPLATE,
            "WHERE player_character_report.player_character_id IN(:characterIds) "
            + "AND "
            + "("
                + "player_character_report.status IS NULL "
                + "OR player_character_report.status = true "
                + "OR player_character_report.status_change_timestamp >= :from"
            + ")");

    private static RowMapper<LadderPlayerCharacterReport> STD_MAPPER;

    private final NamedParameterJdbcTemplate template;
    private final ConversionService conversionService;

    @Autowired
    public LadderPlayerCharacterReportDAO
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
        if(STD_MAPPER == null) STD_MAPPER = (rs, i)->new LadderPlayerCharacterReport(
            LadderSearchDAO.getLadderTeamMemberMapper().mapRow(rs, i),
            PlayerCharacterReportDAO.getStdRowMapper().mapRow(rs, i)
        );
    }

    public static RowMapper<LadderPlayerCharacterReport> getStdRowMapper()
    {
        return STD_MAPPER;
    }

    public List<LadderPlayerCharacterReport> findAll()
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("from", SC2Pulse.offsetDateTime().minusDays(HIDE_DENIED_REPORTS_DAYS))
            .addValue("cheaterReportType", conversionService
                .convert(PlayerCharacterReport.PlayerCharacterReportType.CHEATER, Integer.class));
        return template.query(FIND_REPORTS, params, STD_MAPPER);
    }

    public List<LadderPlayerCharacterReport> findByCharacterIds(Set<Long> characterIds)
    {
        if(characterIds.isEmpty()) return List.of();

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("characterIds",  characterIds)
            .addValue("from", SC2Pulse.offsetDateTime().minusDays(HIDE_DENIED_REPORTS_DAYS))
            .addValue("cheaterReportType", conversionService
                .convert(PlayerCharacterReport.PlayerCharacterReportType.CHEATER, Integer.class));
        return template.query(FIND_REPORTS_BY_CHARACTER_IDS, params, STD_MAPPER);
    }

}
