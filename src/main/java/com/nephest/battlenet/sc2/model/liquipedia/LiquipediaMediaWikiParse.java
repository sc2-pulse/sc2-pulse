// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.liquipedia;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy.class)
public class LiquipediaMediaWikiParse
{

    private String title;
    private Long pageId, revId;
    private LiquipediaMediaWikiText text;

    public LiquipediaMediaWikiParse()
    {
    }

    public LiquipediaMediaWikiParse(String title, Long pageId, Long revId, LiquipediaMediaWikiText text)
    {
        this.title = title;
        this.pageId = pageId;
        this.revId = revId;
        this.text = text;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public Long getPageId()
    {
        return pageId;
    }

    public void setPageId(Long pageId)
    {
        this.pageId = pageId;
    }

    public Long getRevId()
    {
        return revId;
    }

    public void setRevId(Long revId)
    {
        this.revId = revId;
    }

    public LiquipediaMediaWikiText getText()
    {
        return text;
    }

    public void setText(LiquipediaMediaWikiText text)
    {
        this.text = text;
    }

}
