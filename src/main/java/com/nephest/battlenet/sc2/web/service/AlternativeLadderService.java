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
import com.nephest.battlenet.sc2.web.service.blizzard.BlizzardSC2API;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import reactor.util.function.Tuple3;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class AlternativeLadderService
{

    private static final Logger LOG = LoggerFactory.getLogger(AlternativeLadderService.class);

    public static final long FIRST_DIVISION_ID = 33080L;

    @Autowired
    private AlternativeLadderService alternativeLadderService;

    private final BlizzardSC2API api;
    private final LeagueDAO leagueDao;
    private final LeagueTierDAO leagueTierDao;
    private final DivisionDAO divisionDao;
    private final TeamDAO teamDao;
    private final PlayerCharacterDAO playerCharacterDao;
    private final TeamMemberDAO teamMemberDao;
    private final Validator validator;

    @Autowired
    public AlternativeLadderService
    (
        BlizzardSC2API api,
        LeagueDAO leagueDao,
        LeagueTierDAO leagueTierDao,
        DivisionDAO divisionDao,
        TeamDAO teamDao,
        PlayerCharacterDAO playerCharacterDao,
        TeamMemberDAO teamMemberDao,
        Validator validator
    )
    {
        this.api = api;
        this.leagueDao = leagueDao;
        this.leagueTierDao = leagueTierDao;
        this.divisionDao = divisionDao;
        this.teamDao = teamDao;
        this.playerCharacterDao = playerCharacterDao;
        this.teamMemberDao = teamMemberDao;
        this.validator = validator;
    }

    public static final int ALTERNATIVE_LADDER_ERROR_THRESHOLD = 50;
    public static final int BATCH_SIZE = 10;
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

    public void discoverSeason(Season season)
    {
        LOG.info("Discovering {} ladders", season);
        ConcurrentLinkedQueue<Tuple3<Region, BlizzardPlayerCharacter[], Long>> profileIds = get1v1ProfileLadderIds(season);
        LOG.info("{} {} ladders found", profileIds.size(), season);
        api.getProfile1v1Ladders(profileIds, BATCH_SIZE)
            .doOnNext((r)->saveProfileLadder(season, r.getT1(), r.getT2()))
            .sequential().blockLast();
    }

    private ConcurrentLinkedQueue<Tuple3<Region, BlizzardPlayerCharacter[], Long>> get1v1ProfileLadderIds(Season season)
    {
        ConcurrentLinkedQueue<Tuple3<Region, BlizzardPlayerCharacter[], Long>> profileLadderIds =
        new ConcurrentLinkedQueue<>();
        long lastDivision = divisionDao.findLastDivision(season.getBattlenetId() - 1, season.getRegion(),
            QueueType.LOTV_1V1, TeamType.ARRANGED).orElse(FIRST_DIVISION_ID) + 1;
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
        for(BlizzardProfileTeam bTeam : ladder.getLadderTeams())
        {
            Errors errors = new BeanPropertyBindingResult(bTeam, bTeam.toString());
            validator.validate(bTeam, errors);
            if(errors.hasErrors() || !isValidTeam(bTeam, 1)) continue;

            BlizzardProfileTeamMember bMember = bTeam.getTeamMembers()[0];
            PlayerCharacter playerCharacter = playerCharacterDao
                .findByRegionAndBattlenetId(id.getT1(), bMember.getId()).orElse(null);
            //skip new players for now
            if(playerCharacter == null) continue;

            String name = playerCharacter.getName().startsWith(bMember.getName())
                ? playerCharacter.getName() : bMember.getName() + "#1";
            playerCharacter.setName(name);
            playerCharacterDao.merge(playerCharacter);

            Map.Entry<Team, List<TeamMember>> teamEntry =
                updateOrCreate1v1Team(season, playerCharacter, bTeam, ladder.getLeagueType(), division);
            TeamMember member = teamEntry.getValue().get(0);
            member.setGamesPlayed(bMember.getFavoriteRace(), bTeam.getWins() + bTeam.getLosses());
            members.add(member);
        }
        if(members.size() > 0) teamMemberDao.merge(members.toArray(new TeamMember[0]));
        LOG.debug("Ladder saved: {} {}", id.getT1(), id.getT3());
    }

    private Division getOrCreate1v1Division(Season season, BaseLeague.LeagueType leagueType, long battlenetId)
    {
        return divisionDao
            .findDivision(season.getBattlenetId(), season.getRegion(), QueueType.LOTV_1V1, TeamType.ARRANGED,battlenetId)
            .orElseGet(()->create1v1Division(season, leagueType, battlenetId));
    }

    private Division create1v1Division(Season season, BaseLeague.LeagueType leagueType, long battlenetId)
    {
        LeagueTier tier = leagueTierDao.findByLadder(
            season.getBattlenetId(), season.getRegion(), leagueType, QueueType.LOTV_1V1,TeamType.ARRANGED, ALTERNATIVE_TIER)
            .orElseGet(()->{
                League league = leagueDao
                    .merge(new League(null, season.getId(), leagueType, QueueType.LOTV_1V1, TeamType.ARRANGED));
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
        BaseLeague.LeagueType leagueType,
        Division division
    )
    {
        Map.Entry<Team, List<TeamMember>> result =
            updateExisting1v1Team(season, playerCharacter, bTeam, leagueType, division);
        if(result != null) return result;
        return create1v1Team(season, playerCharacter, bTeam, leagueType, division);
    }

    private Map.Entry<Team, List<TeamMember>> updateExisting1v1Team
    (
        Season season,
        PlayerCharacter playerCharacter,
        BlizzardProfileTeam bTeam,
        BaseLeague.LeagueType leagueType,
        Division division
    )
    {
        Map.Entry<Team, List<TeamMember>> teamEntry = teamDao.find1v1TeamByFavoriteRace(
            season.getBattlenetId(),playerCharacter, bTeam.getTeamMembers()[0].getFavoriteRace()).orElse(null);
        if(teamEntry != null)
        {
            Team team = teamEntry.getKey();
            team.setDivisionId(division.getId());
            team.setLeagueType(leagueType);
            team.setRating(bTeam.getRating());
            team.setWins(bTeam.getWins());
            team.setLosses(bTeam.getLosses());
            team.setPoints(bTeam.getPoints());
            teamDao.mergeById(team);
            return teamEntry;
        }

        return null;
    }

    private Map.Entry<Team, List<TeamMember>> create1v1Team
    (
        Season season,
        PlayerCharacter playerCharacter,
        BlizzardProfileTeam bTeam,
        BaseLeague.LeagueType leagueType,
        Division division
    )
    {
        Team team = new Team
        (
            null,
            season.getBattlenetId(),
            season.getRegion(),
            new BaseLeague(leagueType, QueueType.LOTV_1V1, TeamType.ARRANGED),
            ALTERNATIVE_TIER,
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
