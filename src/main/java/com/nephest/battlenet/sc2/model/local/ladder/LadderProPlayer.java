// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder;

import com.nephest.battlenet.sc2.model.local.ProPlayer;
import com.nephest.battlenet.sc2.model.local.ProTeam;
import com.nephest.battlenet.sc2.model.local.SocialMediaLink;

import java.util.List;

public class LadderProPlayer
{

    private final ProPlayer proPlayer;
    private final ProTeam proTeam;
    private final List<SocialMediaLink> links;

    public LadderProPlayer(ProPlayer proPlayer, ProTeam proTeam, List<SocialMediaLink> links)
    {
        this.proPlayer = proPlayer;
        this.proTeam = proTeam;
        this.links = links;
    }

    public ProPlayer getProPlayer()
    {
        return proPlayer;
    }

    public ProTeam getProTeam()
    {
        return proTeam;
    }

    public List<SocialMediaLink> getLinks()
    {
        return links;
    }

}
