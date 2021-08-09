// Copyright (C) 2020-2021 Oleksandr Masniuk
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

    private final Integer ratingCurrent;
    private final Integer gamesPlayedCurrent;
    private final Integer rankCurrent;

    @NotNull
    private final LadderTeamMember members;

    public LadderDistinctCharacter
    (
        BaseLeague.LeagueType leagueMax,
        Integer ratingMax,
        Integer ratingCurrent,
        Account account,
        PlayerCharacter character,
        Clan clan,
        String proNickname,
        String proTeam,
        Integer confirmedCheaterReportId,
        Integer terranGamesPlayed,
        Integer protossGamesPlayed,
        Integer zergGamesPlayed,
        Integer randomGamesPlayed,
        Integer totalGamesPlayed,
        Integer gamesPlayedCurrent,
        Integer rankCurrent
    )
    {
        this.leagueMax = leagueMax;
        this.ratingMax = ratingMax;
        this.ratingCurrent = ratingCurrent;
        this.totalGamesPlayed = totalGamesPlayed;
        this.gamesPlayedCurrent = gamesPlayedCurrent;
        this.rankCurrent = rankCurrent;
        this.members = new LadderTeamMember
        (
            account,
            character,
            clan,
            proNickname,
            proTeam,
            confirmedCheaterReportId,
            terranGamesPlayed,
            protossGamesPlayed,
            zergGamesPlayed,
            randomGamesPlayed
        );
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

    public Integer getRatingCurrent()
    {
        return ratingCurrent;
    }

    public Integer getGamesPlayedCurrent()
    {
        return gamesPlayedCurrent;
    }

    public Integer getRankCurrent()
    {
        return rankCurrent;
    }

    public LadderTeamMember getMembers()
    {
        return members;
    }

}
