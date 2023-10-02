// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.community;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.nephest.battlenet.sc2.model.local.ladder.LadderProPlayer;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeam;

public class LadderVideoStream
{

    private final VideoStream stream;
    private final LadderProPlayer proPlayer;
    private final LadderTeam team;
    private CommunityService.Featured featured;

    public LadderVideoStream(VideoStream stream, LadderProPlayer proPlayer, LadderTeam team)
    {
        this.stream = stream;
        this.proPlayer = proPlayer;
        this.team = team;
    }

    @JsonCreator
    public LadderVideoStream
    (
        VideoStream stream,
        LadderProPlayer proPlayer,
        LadderTeam team,
        CommunityService.Featured featured
    )
    {
        this.stream = stream;
        this.proPlayer = proPlayer;
        this.team = team;
        this.featured = featured;
    }

    public VideoStream getStream()
    {
        return stream;
    }

    public LadderProPlayer getProPlayer()
    {
        return proPlayer;
    }

    public LadderTeam getTeam()
    {
        return team;
    }

    public CommunityService.Featured getFeatured()
    {
        return featured;
    }

    public void setFeatured(CommunityService.Featured featured)
    {
        this.featured = featured;
    }

}
