// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.liquipedia;

import java.util.List;

public class LiquipediaPlayer
{

    private String name, queryName;
    private List<String> links;

    public LiquipediaPlayer()
    {
    }

    public LiquipediaPlayer(String name, String queryName, List<String> links)
    {
        this.name = name;
        this.queryName = queryName;
        this.links = links;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getQueryName()
    {
        return queryName;
    }

    public void setQueryName(String queryName)
    {
        this.queryName = queryName;
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
