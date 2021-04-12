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

    public void updateSeason(Season season, QueueType[] queueTypes, BaseLeague.LeagueType[] leagues)
    {
        LOG.debug("Updating season {}", season);
        List<Long> divisions = divisionDao.findDivisionIds
        (
            season.getBattlenetId(),
            season.getRegion(),
            leagues,
            queueTypes, TeamType.ARRANGED
        );
        ConcurrentLinkedQueue<Tuple3<Region, BlizzardPlayerCharacter[], Long>> profileLadderIds =
            new ConcurrentLinkedQueue<>();
        api.getProfileLadderIds(season.getRegion(), divisions)
            .doOnNext(profileLadderIds::add)
            .sequential().blockLast();
        api.getProfileLadders(profileLadderIds, Set.of(queueTypes), BATCH_SIZE)
            .doOnNext((r)->saveProfileLadder(season, r.getT1(), r.getT2()))
            .sequential().blockLast();
    }

    public void updateThenSmartDiscoverSeason(Season season, QueueType[] queueTypes, BaseLeague.LeagueType[] leagues)
    {
        int divisionCount = divisionDao.getDivisionCount(season.getBattlenetId(), season.getRegion(), leagues, QueueType.LOTV_1V1, TeamType.ARRANGED);
        if(divisionCount < SMART_DISCOVERY_MAX.get(season.getRegion()))
        {
            updateThenContinueDiscoverSeason(season, queueTypes, leagues);
        }
        else
        {
            updateSeason(season, queueTypes, leagues);
        }
    }

    public void discoverSeason(Season season)
    {
        long lastDivision = divisionDao.findLastDivision(season.getBattlenetId() - 1, season.getRegion())
            .orElse(FIRST_DIVISION_ID) + 1;
        discoverSeason(season, lastDivision);
    }

    public void updateThenContinueDiscoverSeason(Season season, QueueType[] queueTypes, BaseLeague.LeagueType[] leagues)
    {
        updateSeason(season, queueTypes, leagues);
        long lastDivision = divisionDao
            .findLastDivision(season.getBattlenetId(), season.getRegion())
            .orElseGet(()->divisionDao
                .findLastDivision(season.getBattlenetId() - 1, season.getRegion())
                .orElse(FIRST_DIVISION_ID)) + 1;
        discoverSeason(season, lastDivision);
    }

    private void discoverSeason(Season season, long lastDivision)
    {
        LOG.info("Discovering {} ladders", season);

        ConcurrentLinkedQueue<Tuple3<Region, BlizzardPlayerCharacter[], Long>> profileIds =
            getProfileLadderIds(season, lastDivision);
        LOG.info("{} {} ladders found", profileIds.size(), season);
        api.getProfileLadders(profileIds, QueueType.getTypes(StatsService.VERSION), BATCH_SIZE)
            .doOnNext((r)->saveProfileLadder(season, r.getT1(), r.getT2()))
            .sequential().blockLast();
    }

    private ConcurrentLinkedQueue<Tuple3<Region, BlizzardPlayerCharacter[], Long>> getProfileLadderIds
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
        int teamMemberCount = ladder.getLeague().getQueueType().getTeamFormat()
            .getMemberCount(ladder.getLeague().getTeamType());
        int ladderMemberCount = ladder.getLadderTeams().length * teamMemberCount;
        BaseLeague baseLeague = ladder.getLeague();
        Division division = getOrCreateDivision(season, ladder.getLeague(), id.getT3());
        Set<TeamMember> members = new HashSet<>(ladderMemberCount, 1.0F);
        Set<TeamState> states = new HashSet<>(ladder.getLadderTeams().length, 1.0F);
        List<PlayerCharacter> characters = new ArrayList<>();
        List<Tuple4<Account, PlayerCharacter, Team, Race>> newTeams = new ArrayList<>();
        for(BlizzardProfileTeam bTeam : ladder.getLadderTeams())
        {
            Errors errors = new BeanPropertyBindingResult(bTeam, bTeam.toString());
            validator.validate(bTeam, errors);
            if(errors.hasErrors() || !isValidTeam(bTeam, teamMemberCount)) continue;

            Team team = saveTeam(season, baseLeague, bTeam, division);
            if(team == null) continue; //old team, nothing to update

            extractTeamData(season, team, bTeam, newTeams, characters, members, states);
        }
        saveNewCharacterData(newTeams, members, states);
        savePlayerCharacters(characters);
        teamMemberDao.merge(members.toArray(TeamMember[]::new));
        teamStateDAO.saveState(states.toArray(TeamState[]::new));
        LOG.debug("Ladder saved: {} {}", id.getT1(), id.getT3());
    }

    private void extractTeamData
    (
        Season season,
        Team team,
        BlizzardProfileTeam bTeam,
        List<Tuple4<Account, PlayerCharacter, Team, Race>> newTeams,
        List<PlayerCharacter> characters,
        Set<TeamMember> members,
        Set<TeamState> states
    )
    {
        for(BlizzardProfileTeamMember bMember : bTeam.getTeamMembers())
        {
            PlayerCharacter playerCharacter = playerCharacterDao.find(season.getRegion(), bMember.getRealm(), bMember.getId())
                .orElse(null);

            if(playerCharacter == null) {
                addNewAlternativeCharacter(season, team, bMember, newTeams);
            } else {
                addExistingAlternativeCharacter(team, bTeam, playerCharacter, bMember, characters, members, states);
            }
        }
    }


    // This creates fake accounts for new alternative characters. The main update method will override them with the
    // real ones
    private void addNewAlternativeCharacter
    (
        Season season,
        Team team,
        BlizzardProfileTeamMember bMember,
        List<Tuple4<Account, PlayerCharacter, Team, Race>> newTeams
    )
    {
        String fakeBtag = "f#"
            + conversionService.convert(season.getRegion(), Integer.class)
            + bMember.getRealm()
            + bMember.getId();
        Account fakeAccount = new Account(null, Partition.of(season.getRegion()), fakeBtag);
        PlayerCharacter character = PlayerCharacter.of(fakeAccount, season.getRegion(), bMember);
        newTeams.add(Tuples.of(fakeAccount, character, team, bMember.getFavoriteRace()));
    }

    private void addExistingAlternativeCharacter
    (
        Team team,
        BlizzardProfileTeam bTeam,
        PlayerCharacter playerCharacter,
        BlizzardProfileTeamMember bMember,
        List<PlayerCharacter> characters,
        Set<TeamMember> members,
        Set<TeamState> states
    )
    {
        if(!playerCharacter.getName().equals(bMember.getName()))
        {
            playerCharacter.setName(bMember.getName());
            characters.add(playerCharacter);
        }

        TeamMember member = new TeamMember(team.getId(), playerCharacter.getId(), null, null, null, null);
        member.setGamesPlayed(bMember.getFavoriteRace(), bTeam.getWins() + bTeam.getLosses());
        members.add(member);
        states.add(TeamState.of(team));
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

            Team team = curNewTeam.getT3();
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

    public Division getOrCreateDivision
    (Season season, BaseLeague bLeague, long battlenetId)
    {
        return divisionDao
            .findDivision(season.getBattlenetId(), season.getRegion(), bLeague.getQueueType(), bLeague.getTeamType(), battlenetId)
            .orElseGet(()-> createDivision(season, bLeague, battlenetId));
    }

    private Division createDivision
    (Season season, BaseLeague bLeague, long battlenetId)
    {
        LeagueTier tier = leagueTierDao.findByLadder(
            season.getBattlenetId(), season.getRegion(), bLeague.getType(), bLeague.getQueueType(), bLeague.getTeamType(), ALTERNATIVE_TIER)
            .orElseGet(()->{
                League league = leagueDao
                    .merge(new League(null, season.getId(), bLeague.getType(), bLeague.getQueueType(), bLeague.getTeamType()));
                return leagueTierDao.merge(new LeagueTier(null, league.getId(), ALTERNATIVE_TIER, 0, 0));
            });

        return divisionDao
            .merge(new Division(null, tier.getId(), battlenetId));
    }

    private Team saveTeam
    (
        Season season,
        BaseLeague baseLeague,
        BlizzardProfileTeam bTeam,
        Division division
    )
    {
        Team team = new Team
        (
            null,
            season.getBattlenetId(), season.getRegion(),
            baseLeague, ALTERNATIVE_TIER,
            division.getId(), null,
            bTeam.getRating(), bTeam.getWins(), bTeam.getLosses(), 0, bTeam.getPoints()
        );
        return teamDao.mergeLegacy(
            team, bTeam.getTeamMembers(),
            baseLeague.getQueueType() == QueueType.LOTV_1V1
                ? new Race[]{bTeam.getTeamMembers()[0].getFavoriteRace()}
                : Race.EMPTY_RACE_ARRAY
        );
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
