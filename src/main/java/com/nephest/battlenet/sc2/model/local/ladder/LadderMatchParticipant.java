// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder;

import com.nephest.battlenet.sc2.model.local.MatchParticipant;
import javax.validation.constraints.NotNull;

public class LadderMatchParticipant
{

    @NotNull
    private final MatchParticipant participant;

    @NotNull
    private final LadderTeam team;

    @NotNull
    private final LadderTeamState teamState;

    private final String twitchVodUrl;

    public LadderMatchParticipant
    (
        MatchParticipant participant,
        LadderTeam team,
        LadderTeamState teamState,
        String twitchVodUrl
    )
    {
        this.participant = participant;
        this.team = team;
        this.teamState = teamState;
        this.twitchVodUrl = twitchVodUrl;
    }

    public MatchParticipant getParticipant()
    {
        return participant;
    }

    public LadderTeam getTeam()
    {
        return team;
    }

    public LadderTeamState getTeamState()
    {
        return teamState;
    }

    public String getTwitchVodUrl()
    {
        return twitchVodUrl;
    }

}
