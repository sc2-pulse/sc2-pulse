// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.*;
import com.nephest.battlenet.sc2.model.local.dao.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class SeasonGenerator
{

    public static final int DEFAULT_SEASON_ID = 1;
    public static final int DEFAULT_SEASON_YEAR = 2020;
    public static final int DEFAULT_SEASON_NUMBER = 1;
    public static final int DEFAULT_REALM = 1;

    @Autowired
    private AccountDAO accountDAO;

    @Autowired
    private PlayerCharacterDAO playerCharacterDAO;

    @Autowired
    private SeasonDAO seasonDAO;

    @Autowired
    private LeagueDAO leagueDAO;

    @Autowired
    private LeagueTierDAO leagueTierDAO;

    @Autowired
    private DivisionDAO divisionDAO;

    @Autowired
    private TeamDAO teamDAO;

    @Autowired
    private TeamMemberDAO teamMemberDAO;

    @Autowired
    private NamedParameterJdbcTemplate template;

    @Transactional
    public void generateDefaultSeason
    (
        List<Region> regions,
        List<League.LeagueType> leagues,
        List<QueueType> queueTypes,
        TeamType teamType,
        LeagueTier.LeagueTierType tierType,
        int teamsPerLeague
    )
    {
        List<Season> seasons = new ArrayList<>();
        for(Region region : regions)
        {
            seasons.add(seasonDAO.create(new Season(null, DEFAULT_SEASON_ID, region, DEFAULT_SEASON_YEAR, DEFAULT_SEASON_NUMBER)));
        }
        generateSeason(seasons, leagues, queueTypes, teamType, tierType, teamsPerLeague);
    }

    @Transactional
    public void generateSeason
    (
        List<Season> seasons,
        List<League.LeagueType> leagues,
        List<QueueType> queueTypes,
        TeamType teamType,
        LeagueTier.LeagueTierType tierType,
        int teamsPerLeague
    )
    {
        Map<Integer, List<Season>> seasonsGrouped = seasons.stream().collect(Collectors.groupingBy(Season::getBattlenetId));
        int teamCount = 0;
        for(Integer id : seasonsGrouped.keySet().stream().sorted().collect(Collectors.toList()))
            teamCount += generateGroupedSeasons(seasonsGrouped.get(id), leagues, queueTypes, teamType, tierType,teamsPerLeague, teamCount);
    }

    private int generateGroupedSeasons
    (
        List<Season> seasons,
        List<League.LeagueType> leagues,
        List<QueueType> queueTypes,
        TeamType teamType,
        LeagueTier.LeagueTierType tierType,
        int teamsPerLeague,
        int teamCount
    )
    {
        for(Season season : seasons) seasonDAO.merge(season);

        for (QueueType queueType : queueTypes)
        {
            for (League.LeagueType type : leagues)
            {
                for (Season season : seasons)
                {
                    generateLeague(season, type, queueType, teamType, tierType, teamsPerLeague, teamCount);
                    teamCount += teamsPerLeague;
                }
            }
        }
        return teamCount;
    }

    private void generateLeague
    (
        Season season,
        League.LeagueType type,
        QueueType queueType,
        TeamType teamType,
        LeagueTier.LeagueTierType tierType,
        int teamsPerLeague,
        int teamCount
    )
    {
        League league = leagueDAO.create(new League(null, season.getId(), type, queueType, teamType));
        LeagueTier newTier = new LeagueTier
        (
            null, league.getId(), tierType,
            season.getRegion().ordinal() + type.ordinal(),
            season.getRegion().ordinal() + type.ordinal() + 1
        );
        LeagueTier tier = leagueTierDAO.create(newTier);
        Division division = divisionDAO.create(new Division(null, tier.getId(), Long.valueOf(season.getRegion().ordinal() + "" + type.ordinal())));
        for(int teamIx = 0; teamIx < teamsPerLeague; teamIx++)
        {
            Team newTeam = new Team
           (
               null, season.getBattlenetId(), season.getRegion(), league, tier.getType(), division.getId(), BigInteger.valueOf(teamCount),
               (long) teamCount, teamCount, teamCount + 1, teamCount + 2, teamCount + 3
           );
            Team team = teamDAO.create(newTeam);

            for(int memberIx = 0; memberIx < queueType.getTeamFormat().getMemberCount(teamType); memberIx++)
            {
                int accId = Integer.parseInt(teamCount + "" + memberIx);
                Account account = accountDAO.create(new Account(null, "battletag#" + (long) accId));
                PlayerCharacter character = playerCharacterDAO
                    .create(new PlayerCharacter(null, account.getId(),season.getRegion(), (long) accId, DEFAULT_REALM, "character#" + (long) accId));
                teamMemberDAO.create(new TeamMember(team.getId(), character.getId(), 1, 2, 3, 4));
            }
            teamCount++;
        }
    }

    public Account[] generateAccounts(String name, int count)
    {
        Account[] accounts = new Account[count];
        for(int i = 0; i < count; i++) accounts[i] = accountDAO.create(new Account(null, name + "#" + i));
        return accounts;
    }

    public PlayerCharacter[] generateCharacters(String name, Account[] accounts, Region region, long idStart)
    {
        PlayerCharacter[] characters = new PlayerCharacter[accounts.length];
        for(int i = 0; i < accounts.length; i++) characters[i] = playerCharacterDAO
            .create(new PlayerCharacter(null, accounts[i].getId(), region, idStart + i, 1, name + "#" + i));
        return  characters;
    }

    public Team createTeam
    (
        Season season,
        BaseLeague league,
        BaseLeagueTier.LeagueTierType tierType,
        Division division,
        BigInteger battlenetId,
        long rating, int wins, int losses, int ties, int points,
        PlayerCharacter... members
    )
    {
        Team newTeam = new Team
        (
            null, season.getBattlenetId(), season.getRegion(), league, tierType, division.getId(), battlenetId,
            rating, wins, losses, ties, points
        );
        Team team = teamDAO.create(newTeam);
        for(PlayerCharacter member : members) teamMemberDAO.create(new TeamMember(team.getId(), member.getId(), 1, 2, 3, 4));
        return team;
    }

    public void cleanAll()
    {
        JdbcTestUtils.deleteFromTables
        (
            template.getJdbcTemplate(),
            "season",
            "league",
            "league_tier",
            "division",
            "team",
            "team_member",
            "account",
            "player_character",
            "league_stats"
        );
    }

}
