// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder;

import com.nephest.battlenet.sc2.model.BaseLeague.LeagueType;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;

import java.util.EnumMap;
import java.util.Map;

public class MergedLadderSearchStatsResult
{

    private final Map<Region, Long> regionPlayerCount = new EnumMap<>(Region.class);
    private final Map<LeagueType, Long> leaguePlayerCount = new EnumMap<>(LeagueType.class);

    private final Map<Region, Long> regionTeamCount = new EnumMap<>(Region.class);
    private final Map<LeagueType, Long> leagueTeamCount = new EnumMap<>(LeagueType.class);

    private final Map<Region, Long> regionGamesPlayed = new EnumMap<>(Region.class);
    private final Map<LeagueType, Long> leagueGamesPlayed = new EnumMap<>(LeagueType.class);
    private final Map<Race, Long> raceGamesPlayed = new EnumMap<>(Race.class);

    public MergedLadderSearchStatsResult(Map<Region, Map<LeagueType, LadderSearchStatsResult>> stats)
    {
        init(stats);
    }

    private void init(Map<Region, Map<LeagueType, LadderSearchStatsResult>> stats)
    {
        for(Map.Entry<Region,  Map<LeagueType, LadderSearchStatsResult>> regionEntry : stats.entrySet())
        {
            Region region = regionEntry.getKey();
            long curRegionPlayerCount = 0;
            long curRegionCount = 0;
            for(Map.Entry<LeagueType, LadderSearchStatsResult> leagueEntry : regionEntry.getValue().entrySet())
            {
                LeagueType league = leagueEntry.getKey();
                LadderSearchStatsResult curStats = leagueEntry.getValue();

                curRegionPlayerCount += curStats.getLeagueStats().getPlayerCount();
                curRegionCount += curStats.getLeagueStats().getTeamCount();
                leaguePlayerCount.put(league,
                    leaguePlayerCount.getOrDefault(league, 0L) + curStats.getLeagueStats().getPlayerCount());
                leagueTeamCount.put(league,
                    leagueTeamCount.getOrDefault(league, 0L) + curStats.getLeagueStats().getTeamCount());

                for(Race race : Race.values())
                {
                    Integer gamesPlayed;
                    switch(race)
                    {
                        case TERRAN:
                            gamesPlayed = curStats.getLeagueStats().getTerranGamesPlayed();
                            break;
                        case PROTOSS:
                            gamesPlayed = curStats.getLeagueStats().getProtossGamesPlayed();
                            break;
                        case ZERG:
                            gamesPlayed = curStats.getLeagueStats().getZergGamesPlayed();
                            break;
                        case RANDOM:
                            gamesPlayed = curStats.getLeagueStats().getRandomGamesPlayed();
                            break;
                        default:
                            throw new IllegalArgumentException("Unsupported race");
                    }
                    regionGamesPlayed.put(region, regionGamesPlayed.getOrDefault(region, 0L) + gamesPlayed);
                    leagueGamesPlayed.put(league, leagueGamesPlayed.getOrDefault(league, 0L) + gamesPlayed);
                    raceGamesPlayed.put(race, raceGamesPlayed.getOrDefault(race, 0L) + gamesPlayed);
                }
            }
            regionPlayerCount.put(regionEntry.getKey(), curRegionPlayerCount);
            regionTeamCount.put(regionEntry.getKey(), curRegionCount);
        }
    }

    public Map<Region, Long> getRegionPlayerCount()
    {
        return regionPlayerCount;
    }

    public Map<LeagueType, Long> getLeaguePlayerCount()
    {
        return leaguePlayerCount;
    }

    public Map<Region, Long> getRegionTeamCount()
    {
        return regionTeamCount;
    }

    public Map<LeagueType, Long> getLeagueTeamCount()
    {
        return leagueTeamCount;
    }

    public Map<Region, Long> getRegionGamesPlayed()
    {
        return regionGamesPlayed;
    }

    public Map<LeagueType, Long> getLeagueGamesPlayed()
    {
        return leagueGamesPlayed;
    }

    public Map<Race, Long> getRaceGamesPlayed()
    {
        return raceGamesPlayed;
    }

}
