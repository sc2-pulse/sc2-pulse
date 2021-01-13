// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.Race;

public class BaseLocalTeamMember
implements java.io.Serializable
{

    private static final long serialVersionUID = 1L;

    private Integer terranGamesPlayed;
    private Integer protossGamesPlayed;
    private Integer zergGamesPlayed;
    private Integer randomGamesPlayed;

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
    }

    public Integer getTerranGamesPlayed()
    {
        return terranGamesPlayed;
    }

    public void setProtossGamesPlayed(Integer protossGamesPlayed)
    {
        this.protossGamesPlayed = protossGamesPlayed;
    }

    public Integer getProtossGamesPlayed()
    {
        return protossGamesPlayed;
    }

    public void setZergGamesPlayed(Integer zergGamesPlayed)
    {
        this.zergGamesPlayed = zergGamesPlayed;
    }

    public Integer getZergGamesPlayed()
    {
        return zergGamesPlayed;
    }

    public void setRandomGamesPlayed(Integer randomGamesPlayed)
    {
        this.randomGamesPlayed = randomGamesPlayed;
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

}
