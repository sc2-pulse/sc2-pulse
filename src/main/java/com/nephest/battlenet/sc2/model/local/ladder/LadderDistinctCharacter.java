// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.Clan;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import jakarta.validation.constraints.NotNull;

public class LadderDistinctCharacter
{

    @NotNull
    private BaseLeague.LeagueType leagueMax;

    @NotNull
    private Integer ratingMax;

    @NotNull
    private Integer totalGamesPlayed;

    @NotNull
    private final LadderPlayerSearchStats previousStats;

    @NotNull
    private final LadderPlayerSearchStats currentStats;

    @NotNull
    private final LadderTeamMember members;

    public LadderDistinctCharacter
    (
        BaseLeague.LeagueType leagueMax,
        Integer ratingMax,
        Account account,
        PlayerCharacter character,
        Clan clan,
        Long proId,
        String proNickname,
        String proTeam,
        Boolean restrictions,
        Integer terranGamesPlayed,
        Integer protossGamesPlayed,
        Integer zergGamesPlayed,
        Integer randomGamesPlayed,
        Integer totalGamesPlayed,
        LadderPlayerSearchStats previousStats,
        LadderPlayerSearchStats currentStats
    )
    {
        this.leagueMax = leagueMax;
        this.ratingMax = ratingMax;
        this.totalGamesPlayed = totalGamesPlayed;
        this.members = new LadderTeamMember
        (
            account,
            character,
            clan,
            proId,
            proNickname,
            proTeam,
            restrictions,
            terranGamesPlayed,
            protossGamesPlayed,
            zergGamesPlayed,
            randomGamesPlayed
        );
        this.previousStats = previousStats;
        this.currentStats = currentStats;
    }

    public BaseLeague.LeagueType getLeagueMax()
    {
        return leagueMax;
    }

    public void setLeagueMax(BaseLeague.LeagueType leagueMax)
    {
        this.leagueMax = leagueMax;
    }

    public Integer getRatingMax()
    {
        return ratingMax;
    }

    public void setRatingMax(Integer ratingMax)
    {
        this.ratingMax = ratingMax;
    }

    public Integer getTotalGamesPlayed()
    {
        return totalGamesPlayed;
    }

    public void setTotalGamesPlayed(Integer totalGamesPlayed)
    {
        this.totalGamesPlayed = totalGamesPlayed;
    }

    public LadderTeamMember getMembers()
    {
        return members;
    }

    public LadderPlayerSearchStats getPreviousStats()
    {
        return previousStats;
    }

    public LadderPlayerSearchStats getCurrentStats()
    {
        return currentStats;
    }


}
