// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.*;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardPlayerCharacter;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardProfileLadder;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardProfileTeam;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardProfileTeamMember;
import com.nephest.battlenet.sc2.model.local.*;
import com.nephest.battlenet.sc2.model.local.dao.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuple4;
import reactor.util.function.Tuples;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class AlternativeLadderService
{

    private static final Logger LOG = LoggerFactory.getLogger(AlternativeLadderService.class);
    private static final Map<Region, Integer> SMART_DISCOVERY_MAX = Collections.unmodifiableMap(Map.of(
        Region.US, 200,
        Region.EU, 200,
        Region.KR, 50,
        Region.CN, 100
    ));

    public static final long FIRST_DIVISION_ID = 33080L;

    @Autowired
    private AlternativeLadderService alternativeLadderService;

    private final BlizzardSC2API api;
    private final LeagueDAO leagueDao;
    private final LeagueTierDAO leagueTierDao;
    private final DivisionDAO divisionDao;
    private final TeamDAO teamDao;
    private final TeamStateDAO teamStateDAO;
    private final AccountDAO accountDAO;
    private final PlayerCharacterDAO playerCharacterDao;
    private final TeamMemberDAO teamMemberDao;
    private final ConversionService conversionService;
    private final Validator validator;

    @Autowired
    public AlternativeLadderService
    (
        BlizzardSC2API api,
        LeagueDAO leagueDao,
        LeagueTierDAO leagueTierDao,
        DivisionDAO divisionDao,
        TeamDAO teamDao,
        TeamStateDAO teamStateDAO,
        AccountDAO accountDAO,
        PlayerCharacterDAO playerCharacterDao,
        TeamMemberDAO teamMemberDao,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService,
        Validator validator
    )
    {
        this.api = api;
        this.leagueDao = leagueDao;
        this.leagueTierDao = leagueTierDao;
        this.divisionDao = divisionDao;
        this.teamDao = teamDao;
        this.teamStateDAO = teamStateDAO;
        this.accountDAO = accountDAO;
        this.playerCharacterDao = playerCharacterDao;
        this.teamMemberDao = teamMemberDao;
        this.conversionService = conversionService;
        this.validator = validator;
    }

    public static final int ALTERNATIVE_LADDER_ERROR_THRESHOLD = 50;
    public static final int BATCH_SIZE = 30;
    public static final BaseLeagueTier.LeagueTierType ALTERNATIVE_TIER = BaseLeagueTier.LeagueTierType.FIRST;

    public void updateSeason(Season season, BaseLeague.LeagueType[] leagues)
    {
        LOG.debug("Updating season {}", season);
        List<Long> divisions = divisionDao.findDivisionIds
        (
            season.getBattlenetId(),
            season.getRegion(),
            leagues,
            QueueType.LOTV_1V1, TeamType.ARRANGED
        );
        ConcurrentLinkedQueue<Tuple3<Region, BlizzardPlayerCharacter[], Long>> profileLadderIds =
            new ConcurrentLinkedQueue<>();
        api.getProfileLadderIds(season.getRegion(), divisions)
            .doOnNext(profileLadderIds::add)
            .sequential().blockLast();
        api.getProfile1v1Ladders(profileLadderIds, BATCH_SIZE)
            .doOnNext((r)->saveProfileLadder(season, r.getT1(), r.getT2()))
            .sequential().blockLast();
    }

    public void updateThenSmartDiscoverSeason(Season season, BaseLeague.LeagueType[] leagues)
    {
        int divisionCount = divisionDao.getDivisionCount(season.getBattlenetId(), season.getRegion(), leagues, QueueType.LOTV_1V1, TeamType.ARRANGED);
        if(divisionCount < SMART_DISCOVERY_MAX.get(season.getRegion()))
        {
            updateThenContinueDiscoverSeason(season, leagues);
        }
        else
        {
            updateSeason(season, leagues);
        }
    }

    public void discoverSeason(Season season)
    {
        long lastDivision = divisionDao.findLastDivision(season.getBattlenetId() - 1, season.getRegion(),
            QueueType.LOTV_1V1, TeamType.ARRANGED).orElse(FIRST_DIVISION_ID) + 1;
        discoverSeason(season, lastDivision);
    }

    public void updateThenContinueDiscoverSeason(Season season, BaseLeague.LeagueType[] leagues)
    {
        updateSeason(season, leagues);
        long lastDivision = divisionDao
            .findLastDivision(season.getBattlenetId(), season.getRegion(), QueueType.LOTV_1V1, TeamType.ARRANGED)
            .orElseGet(()->divisionDao
                .findLastDivision(season.getBattlenetId() - 1, season.getRegion(),QueueType.LOTV_1V1, TeamType.ARRANGED)
                .orElse(FIRST_DIVISION_ID)) + 1;
        discoverSeason(season, lastDivision);
    }

    private void discoverSeason(Season season, long lastDivision)
    {
        LOG.info("Discovering {} ladders", season);

        ConcurrentLinkedQueue<Tuple3<Region, BlizzardPlayerCharacter[], Long>> profileIds =
            get1v1ProfileLadderIds(season, lastDivision);
        LOG.info("{} {} ladders found", profileIds.size(), season);
        api.getProfile1v1Ladders(profileIds, BATCH_SIZE)
            .doOnNext((r)->saveProfileLadder(season, r.getT1(), r.getT2()))
            .sequential().blockLast();
    }

    private ConcurrentLinkedQueue<Tuple3<Region, BlizzardPlayerCharacter[], Long>> get1v1ProfileLadderIds
    (Season season, long lastDivision)
    {
        ConcurrentLinkedQueue<Tuple3<Region, BlizzardPlayerCharacter[], Long>> profileLadderIds =
        new ConcurrentLinkedQueue<>();
        AtomicInteger discovered = new AtomicInteger(1);
        while(discovered.get() > 0)
        {
            discovered.set(0);
            api.getProfileLadderIds(season.getRegion(), lastDivision,lastDivision + ALTERNATIVE_LADDER_ERROR_THRESHOLD)
                .doOnNext((id)->{
                    profileLadderIds.add(id);
                    discovered.getAndIncrement();
                    LOG.debug("Ladder discovered: {} {}", id.getT1(), id.getT3());
                })
                .sequential()
                .blockLast();

            lastDivision+=ALTERNATIVE_LADDER_ERROR_THRESHOLD;
        }
        return profileLadderIds;
    }

    private void saveProfileLadder(Season season, BlizzardProfileLadder ladder, Tuple3<Region, BlizzardPlayerCharacter[], Long> id)
    {
        alternativeLadderService.updateTeams(season, id, ladder);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateTeams(Season season, Tuple3<Region, BlizzardPlayerCharacter[], Long> id, BlizzardProfileLadder ladder)
    {
        Division division = getOrCreate1v1Division(season, ladder.getLeagueType(), id.getT3());
        Set<TeamMember> members = new HashSet<>(ladder.getLadderTeams().length, 1.0F);
        Set<TeamState> states = new HashSet<>(ladder.getLadderTeams().length, 1.0F);
        List<PlayerCharacter> characters = new ArrayList<>(ladder.getLadderTeams().length);
        List<Tuple4<Account, PlayerCharacter, Team, Race>> newTeams = new ArrayList<>();
        for(BlizzardProfileTeam bTeam : ladder.getLadderTeams())
        {
            Errors errors = new BeanPropertyBindingResult(bTeam, bTeam.toString());
            validator.validate(bTeam, errors);
            if(errors.hasErrors() || !isValidTeam(bTeam, 1)) continue;

            BlizzardProfileTeamMember bMember = bTeam.getTeamMembers()[0];
            PlayerCharacter playerCharacter = playerCharacterDao.find(id.getT1(), bMember.getRealm(), bMember.getId())
                .orElse(null);

            if(playerCharacter == null) {
                addNewAlternativeCharacter(season, division, bTeam, newTeams);
                continue;
            }

            if(!playerCharacter.getName().equals(bMember.getName()))
            {
                playerCharacter.setName(bMember.getName());
                characters.add(playerCharacter);
            }

            Map.Entry<Team, List<TeamMember>> teamEntry =
                updateOrCreate1v1Team(season, playerCharacter, bTeam, division);
            //old team, nothing to update
            if(teamEntry.getKey() == null) continue;
            TeamMember member = teamEntry.getValue().get(0);
            member.setGamesPlayed(bMember.getFavoriteRace(), bTeam.getWins() + bTeam.getLosses());
            members.add(member);
            states.add(TeamState.of(teamEntry.getKey()));
        }
        saveNewCharacterData(newTeams, members, states);
        savePlayerCharacters(characters);
        if(members.size() > 0) teamMemberDao.merge(members.toArray(new TeamMember[0]));
        teamStateDAO.saveState(states.toArray(TeamState[]::new));
        LOG.debug("Ladder saved: {} {}", id.getT1(), id.getT3());
    }


    // This creates fake accounts for new alternative characters. The main update method will override them with the
    // real ones
    private void addNewAlternativeCharacter
    (
        Season season,
        Division division,
        BlizzardProfileTeam bTeam,
        List<Tuple4<Account, PlayerCharacter, Team, Race>> newTeams
    )
    {
        String fakeBtag = "f#"
            + conversionService.convert(season.getRegion(), Integer.class)
            + bTeam.getTeamMembers()[0].getRealm()
            + bTeam.getTeamMembers()[0].getId();
        Account fakeAccount = new Account(null, Partition.of(season.getRegion()), fakeBtag);
        PlayerCharacter character = PlayerCharacter.of(fakeAccount, season.getRegion(), bTeam.getTeamMembers()[0]);
        Team team = new Team(null, season.getRegion(), QueueType.LOTV_1V1, division.getTierId(), division.getId(), null,
            bTeam.getRating(), bTeam.getWins(), bTeam.getLosses(), bTeam.getTies(), bTeam.getPoints());
        newTeams.add(Tuples.of(fakeAccount, character, team, bTeam.getTeamMembers()[0].getFavoriteRace()));
    }

    //this ensures the consistent order for concurrent entities(accounts and players)
    private void saveNewCharacterData
    (List<Tuple4<Account, PlayerCharacter, Team, Race>> newTeams, Set<TeamMember> teamMembers, Set<TeamState> states)
    {
        if(newTeams.size() == 0) return;

        newTeams.sort(Comparator.comparing(a -> a.getT1().getBattleTag()));
        for(Tuple4<Account, PlayerCharacter, Team, Race> curMembers : newTeams) accountDAO.merge(curMembers.getT1());

        newTeams.sort(Comparator.comparing(a -> a.getT2().getBattlenetId()));
        for(Tuple4<Account, PlayerCharacter, Team, Race> curNewTeam : newTeams)
        {
            Account account = curNewTeam.getT1();

            curNewTeam.getT2().setAccountId(account.getId());
            PlayerCharacter character = playerCharacterDao.merge(curNewTeam.getT2());

            Team team = teamDao.merge(curNewTeam.getT3());
            TeamMember teamMember = new TeamMember(team.getId(), character.getId(), null, null, null, null);
            teamMember.setGamesPlayed(curNewTeam.getT4(), team.getWins() + team.getLosses() + team.getTies());
            teamMembers.add(teamMember);
            states.add(TeamState.of(team));
        }
    }

    //this ensures the consistent order for concurrent entities
    private void savePlayerCharacters(List<PlayerCharacter> characters)
    {
        if(characters.isEmpty()) return;

        characters.sort(Comparator.comparing(PlayerCharacter::getBattlenetId));
        for(PlayerCharacter c : characters) playerCharacterDao.merge(c, true);
    }

    public Division getOrCreate1v1Division
    (Season season, QueueType queueType, TeamType teamType, BaseLeague.LeagueType leagueType, long battlenetId)
    {
        return divisionDao
            .findDivision(season.getBattlenetId(), season.getRegion(), queueType, teamType, battlenetId)
            .orElseGet(()->create1v1Division(season, queueType, teamType, leagueType, battlenetId));
    }

    private Division getOrCreate1v1Division(Season season, BaseLeague.LeagueType leagueType, long battlenetId)
    {
        return getOrCreate1v1Division(season, QueueType.LOTV_1V1, TeamType.ARRANGED, leagueType, battlenetId);
    }

    private Division create1v1Division
    (Season season, QueueType queueType, TeamType teamType, BaseLeague.LeagueType leagueType, long battlenetId)
    {
        LeagueTier tier = leagueTierDao.findByLadder(
            season.getBattlenetId(), season.getRegion(), leagueType, queueType, teamType, ALTERNATIVE_TIER)
            .orElseGet(()->{
                League league = leagueDao
                    .merge(new League(null, season.getId(), leagueType, queueType, teamType));
                return leagueTierDao.merge(new LeagueTier(null, league.getId(), ALTERNATIVE_TIER, 0, 0));
            });

        return divisionDao
            .merge(new Division(null, tier.getId(), battlenetId));
    }

    private Map.Entry<Team, List<TeamMember>> updateOrCreate1v1Team
    (
        Season season,
        PlayerCharacter playerCharacter,
        BlizzardProfileTeam bTeam,
        Division division
    )
    {
        Map.Entry<Team, List<TeamMember>> result =
            updateExisting1v1Team(season, playerCharacter, bTeam, division);
        if(result != null) return result;
        return create1v1Team(season, playerCharacter, bTeam, division);
    }

    private Map.Entry<Team, List<TeamMember>> updateExisting1v1Team
    (
        Season season,
        PlayerCharacter playerCharacter,
        BlizzardProfileTeam bTeam,
        Division division
    )
    {
        Map.Entry<Team, List<TeamMember>> teamEntry = teamDao.find1v1TeamByFavoriteRace(
            season.getBattlenetId(),playerCharacter, bTeam.getTeamMembers()[0].getFavoriteRace()).orElse(null);
        if(teamEntry != null)
        {
            Team team = new Team
            (
                teamEntry.getKey().getId(),
                season.getRegion(),
                QueueType.LOTV_1V1,
                division.getTierId(),
                division.getId(),
                teamEntry.getKey().getBattlenetId(),
                bTeam.getRating(), bTeam.getWins(), bTeam.getLosses(), teamEntry.getKey().getTies(), bTeam.getPoints()
            );
            if(!Team.shouldUpdate(teamEntry.getKey(), team)) return new AbstractMap.SimpleEntry<>(null, null);
            if(teamDao.mergeById(team, false) == null) return new AbstractMap.SimpleEntry<>(null, null);

            return new AbstractMap.SimpleEntry<>(team, teamEntry.getValue());
        }

        return null;
    }

    private Map.Entry<Team, List<TeamMember>> create1v1Team
    (
        Season season,
        PlayerCharacter playerCharacter,
        BlizzardProfileTeam bTeam,
        Division division
    )
    {
        Team team = new Team
        (
            null,
            season.getRegion(),
            QueueType.LOTV_1V1,
            division.getTierId(),
            division.getId(),
            null,
            bTeam.getRating(), bTeam.getWins(), bTeam.getLosses(), 0, bTeam.getPoints()
        );
        teamDao.merge(team);
        TeamMember member = new TeamMember(team.getId(), playerCharacter.getId(), null, null, null, null);
        return new AbstractMap.SimpleEntry<>(team, List.of(member));
    }

    private boolean isValidTeam(BlizzardProfileTeam team, int expectedMemberCount)
    {
        /*
            empty teams are messing with the stats numbers
            there are ~0.1% of partial teams, which is a number low enough to consider such teams invalid
            this probably has something to do with players revoking their information from blizzard services
         */
        return team.getTeamMembers().length == expectedMemberCount
            //a team can have 0 games while a team member can have some games played, which is clearly invalid
            && (team.getWins() > 0 || team.getLosses() > 0 || team.getTies() > 0);
    }

}
