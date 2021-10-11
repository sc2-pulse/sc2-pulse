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
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.convert.ConversionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import reactor.util.function.Tuple2;
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
    public static final int LADDER_BATCH_SIZE = StatsService.LADDER_BATCH_SIZE;

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
    private final ClanDAO clanDAO;
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
        ClanDAO clanDAO,
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
        this.clanDAO = clanDAO;
        this.teamMemberDao = teamMemberDao;
        this.conversionService = conversionService;
        this.validator = validator;
    }

    public static final int ALTERNATIVE_LADDER_ERROR_THRESHOLD = 100;
    public static final int LEGACY_LADDER_BATCH_SIZE = 500;
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
        batchUpdateLadders(season, Set.of(queueTypes), profileLadderIds);
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
        batchUpdateLadders(season, QueueType.getTypes(StatsService.VERSION), profileIds);
    }

    private void batchUpdateLadders
    (
        Season season,
        Set<QueueType> queueTypes,
        ConcurrentLinkedQueue<Tuple3<Region, BlizzardPlayerCharacter[], Long>> profileIds
    )
    {
        List<Tuple3<Region, BlizzardPlayerCharacter[], Long>> list = new ArrayList<>(profileIds);
        int batches = (int) Math.ceil(list.size() / (double) LADDER_BATCH_SIZE);
        for(int i = 0; i < batches; i++)
        {
            int to = (i + 1) * LADDER_BATCH_SIZE;
            if (to > list.size()) to = list.size();
            List<Tuple3<Region, BlizzardPlayerCharacter[], Long>> batch =
                list.subList(i * LADDER_BATCH_SIZE, to);
            alternativeLadderService.updateLadders(season, queueTypes, batch);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateLadders
    (
        Season season,
        Set<QueueType> queueTypes,
        List<Tuple3<Region, BlizzardPlayerCharacter[], Long>> ladders
    )
    {
        api.getProfileLadders(ladders, queueTypes)
            .sequential()
            .toStream(BlizzardSC2API.SAFE_REQUESTS_PER_SECOND_CAP * 2)
            .forEach((r)->saveProfileLadder(season, r.getT1(), r.getT2()));
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
            api.getProfileLadderIds(season.getRegion(), lastDivision,lastDivision + LEGACY_LADDER_BATCH_SIZE)
                .doOnNext((id)->{
                    profileLadderIds.add(id);
                    discovered.getAndIncrement();
                    LOG.debug("Ladder discovered: {} {}", id.getT1(), id.getT3());
                })
                .sequential()
                .blockLast();
            if(LEGACY_LADDER_BATCH_SIZE - discovered.get() > ALTERNATIVE_LADDER_ERROR_THRESHOLD) break;
            lastDivision+=LEGACY_LADDER_BATCH_SIZE;
        }
        return profileLadderIds;
    }

    private void saveProfileLadder(Season season, BlizzardProfileLadder ladder, Tuple3<Region, BlizzardPlayerCharacter[], Long> id)
    {
        updateTeams(season, id, ladder);
    }

    private void updateTeams(Season season, Tuple3<Region, BlizzardPlayerCharacter[], Long> id, BlizzardProfileLadder ladder)
    {
        int teamMemberCount = ladder.getLeague().getQueueType().getTeamFormat()
            .getMemberCount(ladder.getLeague().getTeamType());
        int ladderMemberCount = ladder.getLadderTeams().length * teamMemberCount;
        BaseLeague baseLeague = ladder.getLeague();
        Division division = getOrCreateDivision(season, ladder.getLeague(), id.getT3());
        Set<TeamMember> members = new HashSet<>(ladderMemberCount, 1.0F);
        Set<TeamState> states = new HashSet<>(ladder.getLadderTeams().length, 1.0F);
        List<PlayerCharacter> characters = new ArrayList<>();
        List<Tuple2<PlayerCharacter, Clan>> clans = new ArrayList<>();
        List<Tuple2<PlayerCharacter, Clan>> existingCharacterClans = new ArrayList<>();
        List<Tuple4<Account, PlayerCharacter, Team, Race>> newTeams = new ArrayList<>();
        for(BlizzardProfileTeam bTeam : ladder.getLadderTeams())
        {
            Errors errors = new BeanPropertyBindingResult(bTeam, bTeam.toString());
            validator.validate(bTeam, errors);
            if(errors.hasErrors() || !isValidTeam(bTeam, teamMemberCount)) continue;

            Team team = saveTeam(season, baseLeague, bTeam, division);
            if(team == null) continue; //old team, nothing to update

            extractTeamData(season, team, bTeam, newTeams, characters, clans, existingCharacterClans, members, states);
        }
        StatsService.saveClans(clanDAO, clans);
        saveExistingCharacterClans(existingCharacterClans, characters);
        saveNewCharacterData(newTeams, members, states);
        savePlayerCharacters(characters);
        teamMemberDao.merge(members.toArray(TeamMember[]::new));
        teamStateDAO.saveState(states.toArray(TeamState[]::new));
        LOG.debug("Ladder saved: {} {} {}", id.getT1(), id.getT3(), ladder.getLeague());
    }

    private void extractTeamData
    (
        Season season,
        Team team,
        BlizzardProfileTeam bTeam,
        List<Tuple4<Account, PlayerCharacter, Team, Race>> newTeams,
        List<PlayerCharacter> characters,
        List<Tuple2<PlayerCharacter, Clan>> clans,
        List<Tuple2<PlayerCharacter, Clan>> existingCharacterClans,
        Set<TeamMember> members,
        Set<TeamState> states
    )
    {
        for(BlizzardProfileTeamMember bMember : bTeam.getTeamMembers())
        {
            PlayerCharacter playerCharacter = playerCharacterDao.find(season.getRegion(), bMember.getRealm(), bMember.getId())
                .orElse(null);

            if(playerCharacter == null) {
                addNewAlternativeCharacter(season, team, bMember, newTeams, clans);
            } else {
                addExistingAlternativeCharacter(team, bTeam, playerCharacter, bMember, characters, members, states, existingCharacterClans);
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
        List<Tuple4<Account, PlayerCharacter, Team, Race>> newTeams,
        List<Tuple2<PlayerCharacter, Clan>> clans
    )
    {
        String fakeBtag = "f#"
            + conversionService.convert(season.getRegion(), Integer.class)
            + bMember.getRealm()
            + bMember.getId();
        Account fakeAccount = new Account(null, Partition.of(season.getRegion()), fakeBtag);
        PlayerCharacter character = PlayerCharacter.of(fakeAccount, season.getRegion(), bMember);
        if(bMember.getClanTag() != null)
            clans.add(Tuples.of(character, Clan.of(bMember.getClanTag(), character.getRegion())));
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
        Set<TeamState> states,
        List<Tuple2<PlayerCharacter, Clan>> existingCharacterClans
    )
    {
        if(bMember.getClanTag() != null) {
            existingCharacterClans.add(Tuples.of(playerCharacter, Clan.of(bMember.getClanTag(), playerCharacter.getRegion())));
        } else if(playerCharacter.getClanId() != null) {
            playerCharacter.setClanId(null);
            characters.add(playerCharacter);
        }

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

    private void saveExistingCharacterClans(List<Tuple2<PlayerCharacter, Clan>> clans, List<PlayerCharacter> characters)
    {
        if(clans.isEmpty()) return;

        clanDAO.merge(clans.stream()
            .map(Tuple2::getT2)
            .toArray(Clan[]::new)
        );
        for(Tuple2<PlayerCharacter, Clan> t : clans) {
            if(!Objects.equals(t.getT2().getId(), t.getT1().getClanId())) characters.add(t.getT1());
            t.getT1().setClanId(t.getT2().getId());
        }
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
        for(PlayerCharacter c : characters) playerCharacterDao.merge(c);
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
        LeagueTier tier = alternativeLadderService.createLeagueTier(season, bLeague);
        return divisionDao.merge(new Division(null, tier.getId(), battlenetId));
    }

    @Cacheable(cacheNames = "ladder-skeleton")
    public LeagueTier createLeagueTier(Season season, BaseLeague bLeague)
    {
        return leagueTierDao.findByLadder(
            season.getBattlenetId(), season.getRegion(), bLeague.getType(), bLeague.getQueueType(), bLeague.getTeamType(), ALTERNATIVE_TIER)
            .orElseGet(()->{
                League league = leagueDao
                    .merge(new League(null, season.getId(), bLeague.getType(), bLeague.getQueueType(), bLeague.getTeamType()));
                return leagueTierDao.merge(new LeagueTier(null, league.getId(), ALTERNATIVE_TIER, 0, 0));
            });
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
            baseLeague, null,
            teamDao.legacyIdOf(baseLeague, bTeam), division.getId(),
            bTeam.getRating(), bTeam.getWins(), bTeam.getLosses(), 0, bTeam.getPoints()
        );
        return teamDao.merge(team);
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
