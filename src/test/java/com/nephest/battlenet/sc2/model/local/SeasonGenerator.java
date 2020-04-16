/*-
 * =========================LICENSE_START=========================
 * SC2 Ladder Generator
 * %%
 * Copyright (C) 2020 Oleksandr Masniuk
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END=========================
 */
package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.dao.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

@Component
public class SeasonGenerator
{

    public static final long DEFAULT_SEASON_ID = 1L;
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
    public void generateSeason
    (
        List<Region> regions,
        List<League.LeagueType> leagues,
        QueueType queueType,
        TeamType teamType,
        LeagueTier.LeagueTierType tierType,
        int teamsPerLeague
    )
    {
        int teamCount = 0;
        List<Season> seasons = new ArrayList<>();
        for(Region region : regions)
        {
            seasons.add(seasonDAO.create(new Season(null, DEFAULT_SEASON_ID, region, DEFAULT_SEASON_YEAR, DEFAULT_SEASON_NUMBER)));
        }
        for (League.LeagueType type : leagues)
        {
            for(Season season : seasons)
            {
                generateLeague(season, type, queueType, teamType, tierType, teamsPerLeague, teamCount);
                teamCount += teamsPerLeague;
            }
        }
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
        LeagueTier tier = leagueTierDAO.create(new LeagueTier(null, league.getId(), tierType, type.ordinal(), type.ordinal() + 1));
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
                Account account = accountDAO.create(new Account(null, season.getRegion(), (long) accId, "battletag#" + (long) accId));
                PlayerCharacter character = playerCharacterDAO.create(new PlayerCharacter(null, account.getId(), (long) accId, DEFAULT_REALM, "character#" + (long) accId));
                teamMemberDAO.create(new TeamMember(team.getId(), character.getId(), 1, 2, 3, 4));
            }
            teamCount++;
        }
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
