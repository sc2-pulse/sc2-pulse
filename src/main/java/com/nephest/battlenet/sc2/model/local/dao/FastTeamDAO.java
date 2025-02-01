// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.BasicEntityOperations;
import com.nephest.battlenet.sc2.model.local.Team;
import java.math.BigInteger;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/**
 * The main function of this DAO is to improve performance of the most heavy conditional DB
 * operations(i.e. merge). Run conditional batch operation using this DAO, and then use the
 * returned result in real DAO. It's a separate DAO that is not connected/persisted to the main
 * DataSource/DB. It maintains its state, but there is no persistence. You must
 * {@link #load(Region, int)} the data before using it. {@link #remove(Region)} the data when you
 * no longer need it because it takes some resources to maintain it.
 */
@Repository
public class FastTeamDAO
implements BasicEntityOperations<Team>
{

    private static final Logger LOG = LoggerFactory.getLogger(FastTeamDAO.class);

    private final Map<Region, Map<Team, Team>> teams = new EnumMap<>(Region.class);
    private final TeamDAO teamDAO;
    private final Map<Region, Integer> loadedSeasons = new EnumMap<>(Region.class);

    @Autowired
    public FastTeamDAO(TeamDAO teamDAO)
    {
        this.teamDAO = teamDAO;
        for(Region region : Region.values()) teams.put(region, new HashMap<>());
    }

    public boolean load(Region region, int season)
    {
        Integer loadedSeason = loadedSeasons.get(region);
        if(loadedSeason != null && loadedSeason == season) return false;

        try(Stream<Team> teamStream = teamDAO.find(region, season))
        {
            teams.put(region, teamStream.collect(Collectors.toMap(Function.identity(), Function.identity())));
        }

        loadedSeasons.put(region, season);
        LOG.trace("Loaded teams into fast DAO: {} s{}", region, season);
        return true;
    }

    public void remove(Region region)
    {
        teams.get(region).clear();
        loadedSeasons.remove(region);
    }

    public Optional<Team> findById
    (
            QueueType queueType,
            Region region,
            BigInteger legacyId,
            Integer season
    )
    {
        return find(Team.uid(queueType, region, legacyId, season));
    }

    @Override
    public Optional<Team> find(Team team)
    {
        return Optional.ofNullable(teams.get(team.getRegion()).get(team));
    }

    @Override
    public Set<Team> merge(Set<Team> teamsToMerge)
    {
        if(teamsToMerge.isEmpty()) return teamsToMerge;

        Set<Team> merged = new HashSet<>();
        for(Team team : teamsToMerge)
        {
            Map<Team, Team> regionTeams = teams.get(team.getRegion());
            Team existingTeam = regionTeams.get(team);
            if(existingTeam == null || mustUpdate(existingTeam, team))
            {
                regionTeams.put(team, team);
                merged.add(team);
            }
        }

        return merged;
    }

    private static boolean mustUpdate(Team existingTeam, Team newTeam)
    {
        return
        (
            !Objects.equals(existingTeam.getWins(), newTeam.getWins())
            || !Objects.equals(existingTeam.getLosses(), newTeam.getLosses())
            || !Objects.equals(existingTeam.getDivisionId(), newTeam.getDivisionId())
        )
        &&
        (
            existingTeam.getLastPlayed() == null
            || existingTeam.getLastPlayed().isBefore(newTeam.getLastPlayed())
        )
        &&
        (
            existingTeam.getJoined() == null
            || !existingTeam.getJoined().isAfter(newTeam.getJoined())
        );
    }

}
