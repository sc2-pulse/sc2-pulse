// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.Team;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
{

    private static final Logger LOG = LoggerFactory.getLogger(FastTeamDAO.class);

    private final Map<Team, Team> teams = new HashMap<>();
    private final TeamDAO teamDAO;
    private final Map<Region, Integer> loadedSeasons = new EnumMap<>(Region.class);

    @Autowired
    public FastTeamDAO(TeamDAO teamDAO)
    {
        this.teamDAO = teamDAO;
    }

    public synchronized boolean load(Region region, int season)
    {
        Integer loadedSeason = loadedSeasons.get(region);
        if(loadedSeason != null && loadedSeason == season) return false;

        remove(region);
        try(Stream<Team> teamStream = teamDAO.find(region, season))
        {
            teams.putAll(teamStream.collect(Collectors.toMap(Function.identity(), Function.identity())));
        }

        loadedSeasons.put(region, season);
        LOG.trace("Loaded teams into fast DAO: {} s{}", region, season);
        return true;
    }

    public synchronized boolean remove(Region region)
    {
        return teams.keySet().removeIf(t->t.getRegion() == region);
    }

    public Optional<Team> findById
    (
            QueueType queueType,
            Region region,
            BigInteger legacyId,
            Integer season
    )
    {
        return Optional.ofNullable(teams.get(Team.uid(queueType, region, legacyId, season)));
    }

    public Team[] merge(Team... teamsToMerge)
    {
        if(teamsToMerge.length == 0) return new Team[0];

        List<Team> merged = new ArrayList<>();
        for(Team team : teamsToMerge)
        {
            Team existingTeam = teams.get(team);
            if(existingTeam == null)
            {
                teams.put(team, team);
                merged.add(team);
            }
            else if
            (
                !Objects.equals(existingTeam.getWins(), team.getWins())
                    || !Objects.equals(existingTeam.getLosses(), team.getLosses())
                    || !Objects.equals(existingTeam.getDivisionId(), team.getDivisionId())
            )
            {
                existingTeam.setWins(team.getWins());
                existingTeam.setLosses(team.getLosses());
                existingTeam.setTies(team.getTies());
                existingTeam.setDivisionId(team.getDivisionId());
                merged.add(team);
            }
        }

        return merged.toArray(Team[]::new);
    }

}
