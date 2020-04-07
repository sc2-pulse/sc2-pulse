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
package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.*;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardTeam;

import javax.validation.constraints.NotNull;
import java.math.BigInteger;
import java.util.Objects;

public class Team
extends BaseTeam
implements java.io.Serializable
{

    private static final long serialVersionUID = 1l;

    private Long id;

    @NotNull
    private Long divisionId;

    @NotNull
    private BigInteger battlenetId;

    @NotNull
    private Long season;

    @NotNull
    private Region region;

    @NotNull
    private BaseLeague league;

    @NotNull
    private LeagueTier.LeagueTierType tierType;

    public Team(){}

    public Team
    (
        Long id,
        Long season, Region region,
        BaseLeague league,
        LeagueTier.LeagueTierType tierType,
        Long divisionId,
        BigInteger battlenetId,
        Long rating, Integer wins, Integer losses, Integer ties, Integer points
    )
    {
        super(rating, wins, losses, ties, points);
        this.id = id;
        this.divisionId = divisionId;
        this.battlenetId = battlenetId;
        this.season = season;
        this.region = region;
        this.league = league;
        this.tierType = tierType;
    }

    public static final Team of
    (
        Season season,
        League league,
        LeagueTier tier,
        Division division,
        BlizzardTeam bTeam
    )
    {
        return new Team
        (
            null,
            season.getBattlenetId(),
            season.getRegion(),
            league,
            tier.getType(),
            division.getId(),
            bTeam.getId(),
            bTeam.getRating(),
            bTeam.getWins(), bTeam.getLosses(), bTeam.getTies(),
            bTeam.getPoints()
        );
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getDivisionId(), getBattlenetId());
    }

    @Override
    public boolean equals(Object other)
    {
        if(other == null) return false;
        if(other == this) return true;
        if( !(other instanceof Team) ) return false;

        Team otherTeam = (Team) other;
        return getDivisionId() == otherTeam.getDivisionId()
            && getBattlenetId().equals(otherTeam.getBattlenetId());
    }

    @Override
    public String toString()
    {
        return String.format
        (
            "%s[%s %s]",
            getClass().getSimpleName(),
            String.valueOf(getDivisionId()), String.valueOf(getBattlenetId())
        );
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getId()
    {
        return id;
    }

    public void setDivisionId(Long divisionId)
    {
        this.divisionId = divisionId;
    }

    public Long getDivisionId()
    {
        return divisionId;
    }

    public void setBattlenetId(BigInteger battlenetId)
    {
        this.battlenetId = battlenetId;
    }

    public BigInteger getBattlenetId()
    {
        return battlenetId;
    }

    public void setSeason(Long season)
    {
        this.season = season;
    }

    public Long getSeason()
    {
        return season;
    }

    public void setRegion(Region region)
    {
        this.region = region;
    }

    public Region getRegion()
    {
        return region;
    }

    public void setLeagueType(League.LeagueType type)
    {
        league.setType(type);
    }

    public League.LeagueType getLeagueType()
    {
        return league.getType();
    }

    public void setQueueType(QueueType type)
    {
        league.setQueueType(type);
    }

    public QueueType getQueueType()
    {
        return league.getQueueType();
    }

    public void setTeamType(TeamType type)
    {
        league.setTeamType(type);
    }

    public TeamType getTeamType()
    {
        return league.getTeamType();
    }

    public void setTierType(LeagueTier.LeagueTierType tierType)
    {
        this.tierType = tierType;
    }

    public LeagueTier.LeagueTierType getTierType()
    {
        return tierType;
    }

}
