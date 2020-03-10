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
package com.nephest.battlenet.sc2.model.local.ladder;

import java.util.*;

import com.nephest.battlenet.sc2.model.*;
import com.nephest.battlenet.sc2.model.BaseLeague.LeagueType;

public class MergedLadderSearchStatsResult
{

    private final Map<Region, Long> regionPlayerCount = new EnumMap(Region.class);
    private final Map<LeagueType, Long> leaguePlayerCount = new EnumMap(LeagueType.class);

    private final Map<Region, Long> regionTeamCount = new EnumMap(Region.class);
    private final Map<LeagueType, Long> leagueTeamCount = new EnumMap(LeagueType.class);

    private final Map<Region, Long> regionGamesPlayed = new EnumMap(Region.class);
    private final Map<LeagueType, Long> leagueGamesPlayed = new EnumMap(LeagueType.class);
    private final Map<Race, Long> raceGamesPlayed = new EnumMap(Race.class);

    public MergedLadderSearchStatsResult(Map<Region, Map<LeagueType, LadderSearchStatsResult>> stats)
    {
        init(stats);
    }

    private final void init(Map<Region, Map<LeagueType, LadderSearchStatsResult>> stats)
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

                curRegionPlayerCount += curStats.getPlayerCount();
                curRegionCount += curStats.getTeamCount();
                leaguePlayerCount.put(league, leaguePlayerCount.getOrDefault(league, 0l) + curStats.getPlayerCount());
                leagueTeamCount.put(league, leagueTeamCount.getOrDefault(league, 0l) + curStats.getTeamCount());

                for(Map.Entry<Race, Long> raceEntry : curStats.getGamesPlayed().entrySet())
                {
                    Race race = raceEntry.getKey();
                    Long gamesPlayed = raceEntry.getValue();

                    regionGamesPlayed.put(region, regionGamesPlayed.getOrDefault(region, 0l) + gamesPlayed);
                    leagueGamesPlayed.put(league, leagueGamesPlayed.getOrDefault(league, 0l) + gamesPlayed);
                    raceGamesPlayed.put(race, raceGamesPlayed.getOrDefault(race, 0l) + gamesPlayed);
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
