// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.TeamType;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Objects;

public class PlayerCharacterStats
implements Serializable
{

    private static final long serialVersionUID = 6L;

    private Long id;

    @NotNull
    private Long playerCharacterId;

    @NotNull
    private QueueType queueType;

    @NotNull
    private TeamType teamType;

    private Race race;

    @NotNull
    private Integer ratingMax;

    @NotNull
    private BaseLeague.LeagueType leagueMax;

    @NotNull
    private Integer gamesPlayed;

    public PlayerCharacterStats(){}

    public PlayerCharacterStats
    (
        Long id,
        @NotNull Long playerCharacterId,
        @NotNull QueueType queueType,
        @NotNull TeamType teamType,
        Race race,
        @NotNull Integer ratingMax,
        @NotNull BaseLeague.LeagueType leagueMax,
        @NotNull Integer gamesPlayed
    )
    {
        this.id = id;
        this.playerCharacterId = playerCharacterId;
        this.queueType = queueType;
        this.teamType = teamType;
        this.race = race;
        this.ratingMax = ratingMax;
        this.leagueMax = leagueMax;
        this.gamesPlayed = gamesPlayed;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlayerCharacterStats that = (PlayerCharacterStats) o;
        return Objects.equals(getPlayerCharacterId(), that.getPlayerCharacterId())
            && getQueueType() == that.getQueueType()
            && getTeamType() == that.getTeamType()
            && getRace() == that.getRace();
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getPlayerCharacterId(), getQueueType(), getTeamType(), getRace());
    }

    @Override
    public String toString()
    {
        return String.format
        (
            "%s[%s %s %s %s]",
            PlayerCharacterStats.class.getSimpleName(),
            getPlayerCharacterId(),
            getQueueType(),
            getTeamType(),
            getRace()
        );
    }

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getPlayerCharacterId()
    {
        return playerCharacterId;
    }

    public void setPlayerCharacterId(Long playerCharacterId)
    {
        this.playerCharacterId = playerCharacterId;
    }

    public QueueType getQueueType()
    {
        return queueType;
    }

    public void setQueueType(QueueType queueType)
    {
        this.queueType = queueType;
    }

    public TeamType getTeamType()
    {
        return teamType;
    }

    public void setTeamType(TeamType teamType)
    {
        this.teamType = teamType;
    }

    public Race getRace()
    {
        return race;
    }

    public void setRace(Race race)
    {
        this.race = race;
    }

    public Integer getRatingMax()
    {
        return ratingMax;
    }

    public void setRatingMax(Integer ratingMax)
    {
        this.ratingMax = ratingMax;
    }

    public BaseLeague.LeagueType getLeagueMax()
    {
        return leagueMax;
    }

    public void setLeagueMax(BaseLeague.LeagueType leagueMax)
    {
        this.leagueMax = leagueMax;
    }

    public Integer getGamesPlayed()
    {
        return gamesPlayed;
    }

    public void setGamesPlayed(Integer gamesPlayed)
    {
        this.gamesPlayed = gamesPlayed;
    }

}
