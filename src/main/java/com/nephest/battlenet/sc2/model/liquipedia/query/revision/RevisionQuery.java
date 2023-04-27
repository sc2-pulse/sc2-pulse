// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.liquipedia.query.revision;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy.class)
public class RevisionQuery
{

    private List<RevisionPage> pages;

    public RevisionQuery()
    {
    }

    public RevisionQuery(List<RevisionPage> pages)
    {
        this.pages = pages;
    }

    public List<RevisionPage> getPages()
    {
        return pages;
    }

    public void setPages(List<RevisionPage> pages)
    {
        this.pages = pages;
    }

}
