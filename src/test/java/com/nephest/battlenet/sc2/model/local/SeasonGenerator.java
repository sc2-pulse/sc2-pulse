// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.BaseMatch;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardProfileTeam;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardProfileTeamMember;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.DivisionDAO;
import com.nephest.battlenet.sc2.model.local.dao.LeagueDAO;
import com.nephest.battlenet.sc2.model.local.dao.LeagueTierDAO;
import com.nephest.battlenet.sc2.model.local.dao.MatchDAO;
import com.nephest.battlenet.sc2.model.local.dao.MatchParticipantDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.local.dao.SC2MapDAO;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamMemberDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamStateDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderDistinctCharacter;
import com.nephest.battlenet.sc2.model.local.ladder.LadderPlayerSearchStats;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SeasonGenerator
{

    public static final int DEFAULT_SEASON_ID = 1;
    public static final Region DEFAULT_SEASON_REGION = Region.EU;
    public static final int DEFAULT_SEASON_YEAR = 2020;
    public static final int DEFAULT_SEASON_NUMBER = 1;
    public static final LocalDate DEFAULT_SEASON_START = LocalDate.of(2020, 1, 1);
    public static final LocalDate DEFAULT_SEASON_END = DEFAULT_SEASON_START.plusMonths(1);
    public static final BaseLeague.LeagueType DEFAULT_LEAGUE_TYPE = BaseLeague.LeagueType.BRONZE;
    public static final QueueType DEFAULT_QUEUE = QueueType.LOTV_1V1;
    public static final BaseLeagueTier.LeagueTierType DEFAULT_TIER =
        BaseLeagueTier.LeagueTierType.FIRST;
    public static final TeamType DEFAULT_TEAM_TYPE = TeamType.ARRANGED;
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
    private TeamStateDAO teamStateDAO;

    @Autowired
    private TeamMemberDAO teamMemberDAO;

    @Autowired
    private MatchDAO matchDAO;

    @Autowired
    private SC2MapDAO mapDAO;

    @Autowired
    private MatchParticipantDAO matchParticipantDAO;

    @Autowired
    private NamedParameterJdbcTemplate template;

    @Transactional
    public void generateDefaultSeason(int teams, boolean spreadRaces)
    {
        generateDefaultSeason
        (
            List.of(DEFAULT_SEASON_REGION),
            List.of(DEFAULT_LEAGUE_TYPE),
            List.of(DEFAULT_QUEUE),
            DEFAULT_TEAM_TYPE,
            DEFAULT_TIER,
            teams,
            spreadRaces
        );
    }

    @Transactional
    public void generateDefaultSeason(int teams)
    {
        generateDefaultSeason(teams, false);
    }

    @Transactional
    public void generateDefaultSeason
    (
        List<Region> regions,
        List<League.LeagueType> leagues,
        List<QueueType> queueTypes,
        TeamType teamType,
        LeagueTier.LeagueTierType tierType,
        int teamsPerLeague,
        boolean spreadRaces
    )
    {
        List<Season> seasons = new ArrayList<>();
        for(Region region : regions)
        {
            seasons.add(seasonDAO.create(new Season(
                null, DEFAULT_SEASON_ID, region,
                DEFAULT_SEASON_YEAR, DEFAULT_SEASON_NUMBER,
                DEFAULT_SEASON_START, DEFAULT_SEASON_END
            )));
        }
        generateSeason(seasons, leagues, queueTypes, teamType, tierType, teamsPerLeague, spreadRaces);
    }

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
        generateDefaultSeason(regions, leagues, queueTypes, teamType, tierType, teamsPerLeague, false);
    }

    @Transactional
    public void generateSeason
    (
        List<Season> seasons,
        List<League.LeagueType> leagues,
        List<QueueType> queueTypes,
        TeamType teamType,
        LeagueTier.LeagueTierType tierType,
        int teamsPerLeague,
        boolean spreadRaces
    )
    {
        Map<Integer, List<Season>> seasonsGrouped = seasons.stream().collect(Collectors.groupingBy(Season::getBattlenetId));
        int teamCount = 0;
        for(Integer id : seasonsGrouped.keySet().stream().sorted().collect(Collectors.toList()))
            teamCount += generateGroupedSeasons(seasonsGrouped.get(id), leagues, queueTypes, teamType, tierType, teamsPerLeague, teamCount, spreadRaces);
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
        generateSeason(seasons, leagues, queueTypes, teamType, tierType, teamsPerLeague, false);
    }

    private int generateGroupedSeasons
    (
        List<Season> seasons,
        List<League.LeagueType> leagues,
        List<QueueType> queueTypes,
        TeamType teamType,
        LeagueTier.LeagueTierType tierType,
        int teamsPerLeague,
        int teamCount,
        boolean spreadRaces
    )
    {
        for(Season season : seasons) seasonDAO.merge(season);

        for (QueueType queueType : queueTypes)
        {
            for (League.LeagueType type : leagues)
            {
                for (Season season : seasons)
                {
                    generateLeague(season, type, queueType, teamType, tierType, teamsPerLeague, teamCount, spreadRaces);
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
        int teamCount,
        boolean spreadRaces
    )
    {
        ZoneOffset offset = SC2Pulse.offsetDateTime().getOffset();
        OffsetDateTime seasonStart = season.getStart().atStartOfDay(offset).toOffsetDateTime();
        League league = leagueDAO.create(new League(null, season.getId(), type, queueType, teamType));
        LeagueTier newTier = new LeagueTier
        (
            null, league.getId(), tierType,
            season.getRegion().ordinal() + type.ordinal(),
            season.getRegion().ordinal() + type.ordinal() + 1
        );
        LeagueTier tier = leagueTierDAO.create(newTier);
        Division division = divisionDAO.create(new Division(null, tier.getId(),
            Long.valueOf((teamsPerLeague > 0 ? teamCount / teamsPerLeague : 0) + String.valueOf(season.getRegion().ordinal()) + type.ordinal())));
        Race[] races = Race.values();
        for(int teamIx = 0; teamIx < teamsPerLeague; teamIx++)
        {
            Race race = races[teamIx % races.length];
            PlayerCharacter[] characters = new PlayerCharacter[queueType.getTeamFormat().getMemberCount(teamType)];
            BlizzardProfileTeamMember[] bCharacters = new BlizzardProfileTeamMember[characters.length];
            BlizzardProfileTeam bTeam = new BlizzardProfileTeam();
            bTeam.setTeamMembers(bCharacters);
            for(int memberIx = 0; memberIx < queueType.getTeamFormat().getMemberCount(teamType); memberIx++) {
                int accId = Integer.parseInt(teamCount + String.valueOf(memberIx));
                Account account = accountDAO.create(
                    new Account(null, Partition.of(season.getRegion()), "battletag#" + (long) accId));
                PlayerCharacter character = playerCharacterDAO.create(
                    new PlayerCharacter(null, account.getId(), season.getRegion(), (long) accId, DEFAULT_REALM, "character#" + (long) accId));
                characters[memberIx] = character;
                bCharacters[memberIx] = new BlizzardProfileTeamMember
                (
                    character.getBattlenetId(),
                    character.getRealm(),
                    character.getName(),
                    spreadRaces ? race : Race.RANDOM,
                    null
                );
            }
            Team newTeam = new Team
            (
                null, season.getBattlenetId(), season.getRegion(), league, tier.getType(),
                teamDAO.legacyIdOf(league, bTeam), division.getId(),
                (long) teamCount, teamCount, teamCount + 1, teamCount + 2, teamCount + 3,
                SC2Pulse.offsetDateTime()
            );
            Team team = teamDAO.create(newTeam);
            TeamState teamState = TeamState.of(team);
            teamState.setDateTime(seasonStart);
            teamStateDAO.saveState(Set.of(teamState));

            for(int memberIx = 0; memberIx < queueType.getTeamFormat().getMemberCount(teamType); memberIx++)
            {
                PlayerCharacter character = characters[memberIx];
                TeamMember teamMember = spreadRaces
                    ? new TeamMember
                        (
                            team.getId(),
                            character.getId(),
                            race == Race.TERRAN ? 1 : null,
                            race == Race.PROTOSS ? 1 : null,
                            race == Race.ZERG ? 1 : null,
                            race == Race.RANDOM ? 1 : null
                        )
                    : new TeamMember(team.getId(), character.getId(), 1, 2, 3, 4);
                teamMemberDAO.create(teamMember);
            }
            teamCount++;
        }
    }

    public Account[] generateAccounts(Partition partition, String name, int count)
    {
        Account[] accounts = new Account[count];
        for(int i = 0; i < count; i++)
            accounts[i] = accountDAO.create(new Account(null, partition, name + "#" + i));
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
        BigInteger legacyId,
        long rating, int wins, int losses, int ties, int points,
        PlayerCharacter... members
    )
    {
        Team newTeam = new Team
        (
            null, season.getBattlenetId(), season.getRegion(), league, tierType,
            legacyId, division.getId(),
            rating, wins, losses, ties, points,
            SC2Pulse.offsetDateTime()
        );
        Team team = teamDAO.create(newTeam);
        for(PlayerCharacter member : members) teamMemberDAO.create(new TeamMember(team.getId(), member.getId(), 1, 2, 3, 4));
        return team;
    }

    @Transactional
    public List<Team> createTeams(boolean spreadRaces, PlayerCharacter... members)
    {
        if(members.length == 0) return List.of();

        List<Team> teams = new ArrayList<>();
        for(int i = 0; i < members.length; i++)
        {
            Team team = new Team
            (
                null, DEFAULT_SEASON_ID, members[i].getRegion(),
                new BaseLeague
                (
                    BaseLeague.LeagueType.BRONZE,
                    QueueType.LOTV_1V1,
                    TeamType.ARRANGED
                ),
                BaseLeagueTier.LeagueTierType.FIRST,
                BigInteger.valueOf(i), 1,
                (long) i, i, 0, 0, 0,
                SC2Pulse.offsetDateTime()
            );
            teams.add(team);
            teamDAO.create(team);
            TeamMember member = new TeamMember(team.getId(), members[i].getId(), null, null, null, null);
            member.setGamesPlayed(spreadRaces ? Race.values()[i % Race.values().length] : Race.RANDOM, i);
            teamMemberDAO.create(member);
        }
        return teams;
    }

    @Transactional
    public List<Team> createTeams(PlayerCharacter... members)
    {
        return createTeams(false, members);
    }

    @Transactional
    public void createMatches
    (
        BaseMatch.MatchType matchType,
        long id1, long id2,
        long[] characterIds1,
        long[] characterIds2,
        OffsetDateTime date,
        Region region,
        Integer duration,
        Integer division,
        int count,
        int... rating
    )
    {
        for(int i = 0; i < count; i++)
            createMatch
            (
                matchType,
                id1, id2,
                characterIds1, characterIds2,
                date.minusSeconds(i),
                region,
                duration + i,
                division,
                rating.length > 0 ? rating[i * 2] : 1, rating.length > 0 ? rating[i * 2 + 1] : 1
            );
    }

    @Transactional
    public void createMatch
    (
        BaseMatch.MatchType matchType,
        long teamId1, long teamId2,
        long[] characterIds1,
        long[] characterIds2,
        OffsetDateTime date,
        Region region,
        Integer duration,
        Integer division,
        int rating1, int rating2
    )
    {
        teamStateDAO.saveState(Set.of(
            new TeamState(teamId1, date, division, 1, rating1),
            new TeamState(teamId2, date, division, 1, rating2)
        ));
        SC2Map map = mapDAO.merge(Set.of(new SC2Map(null, "map"))).iterator().next();
        Match match = matchDAO.merge(Set.of(new Match(null, date, matchType, map.getId(), region, duration)))
            .iterator().next();
        for(long charId : characterIds1)
            matchParticipantDAO.merge(Set.of(new MatchParticipant(match.getId(), charId, BaseMatch.Decision.WIN)));
        for(long charId : characterIds2)
            matchParticipantDAO.merge(Set.of(new MatchParticipant(match.getId(), charId, BaseMatch.Decision.LOSS)));
    }

    @Transactional
    public void createMatch
    (
        long id1, long id2,
        OffsetDateTime odt,
        long offset
    )
    {
        createMatches
        (
            BaseMatch.MatchType._1V1,
            id1, id2,
            new long[]{id1}, new long[]{id2},
            odt,
            Region.EU,
            1, 1, 1, 1, 1
        );
        createMatches
        (
            BaseMatch.MatchType._1V1,
            id1, id2,
            new long[]{id1}, new long[]{id2},
            odt.plusSeconds(MatchDAO.DURATION_OFFSET + offset),
            Region.EU,
            1, 1, 1, 1, 1
        );
    }

    public static Season defaultSeason()
    {
        return new Season
        (
            1,
            DEFAULT_SEASON_ID,
            DEFAULT_SEASON_REGION,
            DEFAULT_SEASON_YEAR,
            DEFAULT_SEASON_NUMBER,
            DEFAULT_SEASON_START,
            DEFAULT_SEASON_END
        );
    }

    public static League defaultLeague()
    {
        return new League
        (
            1,
            1,
            DEFAULT_LEAGUE_TYPE,
            DEFAULT_QUEUE,
            DEFAULT_TEAM_TYPE
        );
    }

    public static LeagueTier defaultTier()
    {
        return new LeagueTier(1, 1, DEFAULT_TIER, 1, 2);
    }

    public static SC2Map defaultMap()
    {
        return new SC2Map(1, "map");
    }

    public static Account defaultAccount(int ix)
    {
        return new Account
        (
            (long) ix + 1 ,
            Partition.GLOBAL,
            "battletag#" + Integer.parseInt(ix + String.valueOf(0))
        );
    }

    public static PlayerCharacter defaultCharacter(int ix)
    {
        long id = (long) ix + 1;
        int accId = Integer.parseInt(ix + String.valueOf(0));
        return new PlayerCharacter
        (
            id,
            id,
            Region.EU,
            (long) accId,
            DEFAULT_REALM,
            "character#" + accId
        );
    }

    public static LadderDistinctCharacter defaultLadderCharacter(int ix)
    {
        return defaultLadderCharacter(null, null, null, null, ix);
    }

    public static LadderDistinctCharacter defaultLadderCharacter
    (
        Clan clan,
        Long proId, String proNickname, String proTeam,
        int ix
    )
    {
        int games = (ix + 1) * 3;
        return new LadderDistinctCharacter
        (
            BaseLeague.LeagueType.BRONZE, ix,
            defaultAccount(ix),
            defaultCharacter(ix),
            clan,
            proId, proNickname, proTeam,
            null,
            null, null, null, games, games,
            new LadderPlayerSearchStats(null, null, null),
            new LadderPlayerSearchStats(ix, games, null)
        );
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
