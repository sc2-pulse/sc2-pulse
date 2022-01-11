// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.BaseClan;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardClan;

import javax.validation.constraints.NotNull;
import java.util.Comparator;
import java.util.Objects;

public class Clan
extends BaseClan
implements java.io.Serializable
{

    private static final long serialVersionUID = 3L;

    public static final Comparator<Clan> NATURAL_ID_COMPARATOR =
        Comparator.comparing(Clan::getTag)
            .thenComparing(Clan::getRegion);

    private Integer id;

    @NotNull
    private Region region;

    private String name;

    private Integer members;
    private Integer activeMembers;
    private Integer avgRating;
    private BaseLeague.LeagueType avgLeagueType;
    private Integer games;

    public Clan(){}

    public Clan(Integer id, @NotNull String tag, @NotNull Region region, String name)
    {
        super(tag);
        this.id = id;
        this.region = region;
        this.name = name;
    }

    public Clan
    (
        Integer id,
        @NotNull String tag,
        @NotNull Region region,
        String name,
        Integer members,
        Integer activeMembers,
        Integer avgRating,
        BaseLeague.LeagueType avgLeagueType,
        Integer games
    )
    {
        this(id, tag, region, name);
        this.members = members;
        this.activeMembers = activeMembers;
        this.avgRating = avgRating;
        this.avgLeagueType = avgLeagueType;
        this.games = games;
    }

    public static Clan of(BlizzardClan bClan, Region region)
    {
        return new Clan(null, bClan.getTag(), region, bClan.getName());
    }

    public static Clan of(String tag, Region region)
    {
        return new Clan(null, tag, region, null);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getTag(), getRegion());
    }

    @Override
    public boolean equals(Object other)
    {
        if (other == null) return false;
        if (other == this) return true;
        if ( !(other instanceof Clan) ) return false;

        Clan o = (Clan) other;
        return getTag().equals(o.getTag())
            && getRegion() == o.getRegion();
    }

    @Override
    public String toString()
    {
        return String.format
        (
            "%s[%s %s]",
            Clan.class.getSimpleName(),
            getTag(),
            getRegion()
        );
    }

    public Integer getId()
    {
        return id;
    }

    public void setId(Integer id)
    {
        this.id = id;
    }

    public Region getRegion()
    {
        return region;
    }

    public void setRegion(@NotNull Region region)
    {
        this.region = region;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public Integer getMembers()
    {
        return members;
    }

    public void setMembers(Integer members)
    {
        this.members = members;
    }

    public Integer getActiveMembers()
    {
        return activeMembers;
    }

    public void setActiveMembers(Integer activeMembers)
    {
        this.activeMembers = activeMembers;
    }

    public Integer getAvgRating()
    {
        return avgRating;
    }

    public void setAvgRating(Integer avgRating)
    {
        this.avgRating = avgRating;
    }

    public BaseLeague.LeagueType getAvgLeagueType()
    {
        return avgLeagueType;
    }

    public void setAvgLeagueType(BaseLeague.LeagueType avgLeagueType)
    {
        this.avgLeagueType = avgLeagueType;
    }

    public Integer getGames()
    {
        return games;
    }

    public void setGames(Integer games)
    {
        this.games = games;
    }

}
