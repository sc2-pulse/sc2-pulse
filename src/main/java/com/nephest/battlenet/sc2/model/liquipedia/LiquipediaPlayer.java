// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.liquipedia;

import java.util.List;

public class LiquipediaPlayer
{

    private List<String> links;

    public LiquipediaPlayer()
    {
    }

    public LiquipediaPlayer(List<String> links)
    {
        this.links = links;
    }

    public List<String> getLinks()
    {
        return links;
    }

    public void setLinks(List<String> links)
    {
        this.links = links;
    }

}
