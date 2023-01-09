// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.Clan;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import javax.validation.constraints.NotNull;

public class LadderDistinctCharacter
{

    @NotNull
    private final BaseLeague.LeagueType leagueMax;

    @NotNull
    private final Integer ratingMax;

    @NotNull
    private final Integer totalGamesPlayed;

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

    public Integer getRatingMax()
    {
        return ratingMax;
    }

    public Integer getTotalGamesPlayed()
    {
        return totalGamesPlayed;
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
