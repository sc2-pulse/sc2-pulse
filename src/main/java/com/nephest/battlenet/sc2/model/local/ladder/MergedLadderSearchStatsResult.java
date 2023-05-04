// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder;

import com.nephest.battlenet.sc2.model.BaseLeague.LeagueType;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import java.util.EnumMap;
import java.util.Map;

public class MergedLadderSearchStatsResult
{

    private final Map<Region, Long> regionTeamCount = new EnumMap<>(Region.class);
    private final Map<LeagueType, Long> leagueTeamCount = new EnumMap<>(LeagueType.class);
    private final Map<Race, Long> raceTeamCount = new EnumMap<>(Race.class);

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
            long curRegionCount = 0;
            for(Map.Entry<LeagueType, LadderSearchStatsResult> leagueEntry : regionEntry.getValue().entrySet())
            {
                LeagueType league = leagueEntry.getKey();
                LadderSearchStatsResult curStats = leagueEntry.getValue();

                curRegionCount += curStats.getLeagueStats().getTeamCount();
                leagueTeamCount.put(league,
                    leagueTeamCount.getOrDefault(league, 0L) + curStats.getLeagueStats().getTeamCount());

                for(Race race : Race.values())
                {
                    Integer gamesPlayed;
                    Integer teamCount;
                    switch(race)
                    {
                        case TERRAN:
                            gamesPlayed = curStats.getLeagueStats().getTerranGamesPlayed();
                            teamCount = curStats.getLeagueStats().getTerranTeamCount();
                            break;
                        case PROTOSS:
                            gamesPlayed = curStats.getLeagueStats().getProtossGamesPlayed();
                            teamCount = curStats.getLeagueStats().getProtossTeamCount();
                            break;
                        case ZERG:
                            gamesPlayed = curStats.getLeagueStats().getZergGamesPlayed();
                            teamCount = curStats.getLeagueStats().getZergTeamCount();
                            break;
                        case RANDOM:
                            gamesPlayed = curStats.getLeagueStats().getRandomGamesPlayed();
                            teamCount = curStats.getLeagueStats().getRandomTeamCount();
                            break;
                        default:
                            throw new IllegalArgumentException("Unsupported race");
                    }
                    regionGamesPlayed.put(region, regionGamesPlayed.getOrDefault(region, 0L) + gamesPlayed);
                    leagueGamesPlayed.put(league, leagueGamesPlayed.getOrDefault(league, 0L) + gamesPlayed);
                    raceGamesPlayed.put(race, raceGamesPlayed.getOrDefault(race, 0L) + gamesPlayed);
                    if(teamCount != null) raceTeamCount
                        .put(race, raceTeamCount.getOrDefault(race, 0L) + teamCount);
                }
            }
            regionTeamCount.put(regionEntry.getKey(), curRegionCount);
        }
    }

    public Map<Region, Long> getRegionTeamCount()
    {
        return regionTeamCount;
    }

    public Map<LeagueType, Long> getLeagueTeamCount()
    {
        return leagueTeamCount;
    }

    public Map<Race, Long> getRaceTeamCount()
    {
        return raceTeamCount;
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
