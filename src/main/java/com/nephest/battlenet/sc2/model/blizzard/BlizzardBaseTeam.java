// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.blizzard;

import com.nephest.battlenet.sc2.model.BaseTeam;

public class BlizzardBaseTeam
extends BaseTeam
{

    public static final Long RATING_MAX = 20000L;

    /*
        blizzard normally returns zeros, not nulls.
        in a very rare occasion some of the values could be null.
        defaulting to zeros in this case
    */
    public BlizzardBaseTeam(){super(null, 0, 0, 0, 0);}

    public BlizzardBaseTeam
    (
        Long rating, Integer wins, Integer loses, Integer ties, Integer points
    )
    {
        /*
            blizzard normally returns zeros, not nulls.
            in a very rare occasion some of the values could be nulls
        */
        super
        (
            rating,
            (wins == null ? 0 : wins),
            (loses == null ? 0 : loses),
            (ties == null ? 0 : ties),
            (points == null ? 0 : points)
        );
    }

    //Blizzard can return unobtainable values here(cheaters?).
    //Nullifying them to avoid ladder pollution
    @Override
    public void setRating(Long rating)
    {
        if(rating != null && rating > RATING_MAX) rating = null;
        super.setRating(rating);
    }

    @Override
    public void setWins(Integer wins)
    {
        if(wins == null) wins = 0;
        super.setWins(wins);
    }

    @Override
    public void setLosses(Integer losses)
    {
        if(losses == null) losses = 0;
        super.setLosses(losses);
    }

    @Override
    public void setTies(Integer ties)
    {
        if(ties == null) ties = 0;
        super.setTies(ties);
    }

    @Override
    public void setPoints(Integer points)
    {
        if(points == null) points = 0;
        super.setPoints(points);
    }

}
