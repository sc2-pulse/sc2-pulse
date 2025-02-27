// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nephest.battlenet.sc2.model.Race;
import jakarta.validation.constraints.NotNull;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.ObjectUtils;

@JsonIgnoreProperties(value={"raceGames"}, allowGetters=true)
public class BaseLocalTeamMember
implements java.io.Serializable
{

    private static final long serialVersionUID = 2L;

    private Integer terranGamesPlayed;
    private Integer protossGamesPlayed;
    private Integer zergGamesPlayed;
    private Integer randomGamesPlayed;

    private transient Map<Race, Integer> raceGames;

    public BaseLocalTeamMember(){}

    public BaseLocalTeamMember
    (
        Integer terranGamesPlayed,
        Integer protossGamesPlayed,
        Integer zergGamesPlayed,
        Integer randomGamesPlayed
    )
    {
        this.terranGamesPlayed = terranGamesPlayed;
        this.protossGamesPlayed = protossGamesPlayed;
        this.zergGamesPlayed = zergGamesPlayed;
        this.randomGamesPlayed = randomGamesPlayed;
    }

    public void setTerranGamesPlayed(Integer terranGamesPlayed)
    {
        this.terranGamesPlayed = terranGamesPlayed;
        this.raceGames = null;
    }

    public Integer getTerranGamesPlayed()
    {
        return terranGamesPlayed;
    }

    public void setProtossGamesPlayed(Integer protossGamesPlayed)
    {
        this.protossGamesPlayed = protossGamesPlayed;
        this.raceGames = null;
    }

    public Integer getProtossGamesPlayed()
    {
        return protossGamesPlayed;
    }

    public void setZergGamesPlayed(Integer zergGamesPlayed)
    {
        this.zergGamesPlayed = zergGamesPlayed;
        this.raceGames = null;
    }

    public Integer getZergGamesPlayed()
    {
        return zergGamesPlayed;
    }

    public void setRandomGamesPlayed(Integer randomGamesPlayed)
    {
        this.randomGamesPlayed = randomGamesPlayed;
        this.raceGames = null;
    }

    public Integer getRandomGamesPlayed()
    {
        return randomGamesPlayed;
    }
    
    public void setGamesPlayed(Race race, Integer gamesPlayed)
    {
        switch(race)
        {
            case TERRAN:
                setTerranGamesPlayed(gamesPlayed);
                break;
            case PROTOSS:
                setProtossGamesPlayed(gamesPlayed);
                break;
            case ZERG:
                setZergGamesPlayed(gamesPlayed);
                break;
            case RANDOM:
                setRandomGamesPlayed(gamesPlayed);
                break;
        }
    }

    public Integer getGamesPlayed(Race race)
    {
        Integer result = null;
        switch(race)
        {
            case TERRAN:
                result = getTerranGamesPlayed();
                break;
            case PROTOSS:
                result = getProtossGamesPlayed();
                break;
            case ZERG:
                result = getZergGamesPlayed();
                break;
            case RANDOM:
                result = getRandomGamesPlayed();
                break;
        }
        return result;
    }

    @JsonIgnore
    public Race getFavoriteRace()
    {
        Integer highestCount = getTerranGamesPlayed();
        Race result = Race.TERRAN;
        for(Race race : Race.values())
        {
            Integer raceGames = getGamesPlayed(race);
            if(ObjectUtils.compare(raceGames, highestCount) > 0)
                {result = race; highestCount = raceGames;}
        }
        return result;
    }

    private void createRaceGamesMap()
    {
        raceGames = Collections.unmodifiableMap(Arrays.stream(Race.values())
            .map(r->new RaceGames(r, getGamesPlayed(r)))
            .filter(rg->rg.games != null)
            .sorted(Comparator.comparing(RaceGames::games).reversed().thenComparing(RaceGames::race))
            .collect(Collectors.toMap(
                RaceGames::race,
                RaceGames::games,
                (l, r)->{throw new IllegalStateException("Unexpected merge operation");},
                ()->new LinkedHashMap<>(Race.values().length)
            )));
    }

    public Map<Race, Integer> getRaceGames()
    {
        if(raceGames == null) createRaceGamesMap();

        return raceGames;
    }

    private record RaceGames(@NotNull Race race, Integer games){}

}
